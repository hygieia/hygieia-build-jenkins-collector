package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.util.Supplier;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import sun.rmi.runtime.Log;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * TeamcityClient implementation that uses RestTemplate and JSONSimple to
 * fetch information from Teamcity instances.
 */
@Component
public class DefaultTeamcityClient implements TeamcityClient {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultTeamcityClient.class);

    private final RestOperations rest;
    private final TeamcitySettings settings;

    private static final String PROJECT_API_URL_SUFFIX = "app/rest/projects";

    private static final String BUILD_DETAILS_URL_SUFFIX = "app/rest/builds";

    private static final String DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss";

    @Autowired
    public DefaultTeamcityClient(Supplier<RestOperations> restOperationsSupplier, TeamcitySettings settings) {
        this.rest = restOperationsSupplier.get();
        this.settings = settings;
    }

    @Override
    public Map<TeamcityProject, Map<jobData, Set<BaseModel>>> getInstanceProjects(String instanceUrl) {
        LOG.debug("Enter getInstanceProjects");
        Map<TeamcityProject, Map<jobData, Set<BaseModel>>> result = new LinkedHashMap<>();
        for (String projectID : settings.getProjectIds()) {
            try {
                String url = joinURL(instanceUrl, new String[]{PROJECT_API_URL_SUFFIX + "/id:" + projectID});
                ResponseEntity<String> responseEntity = makeRestCall(url);
                if (responseEntity == null) {
                    break;
                }
                String returnJSON = responseEntity.getBody();
                if (StringUtils.isEmpty(returnJSON)) {
                    break;
                }
                JSONParser parser = new JSONParser();
                try {
                    JSONObject object = (JSONObject) parser.parse(returnJSON);
                    JSONArray jobs = getJsonArray(object, "project");
                    if (jobs.size() == 0) {
                        break;
                    }
                    for (Object job : jobs) {
                        JSONObject jsonJob = (JSONObject) job;

                        final String projectName = getString(jsonJob, "name");
                        final String projectURL = String.format("%s/%s/id:%s", instanceUrl, PROJECT_API_URL_SUFFIX, projectID);
                        LOG.debug("Process projectName " + projectName + " projectURL " + projectURL);

                        getProjectDetails(projectID, projectName, projectURL, instanceUrl, result);
                    }
                } catch (ParseException e) {
                    LOG.error("Parsing jobs details on instance: " + instanceUrl, e);
                }
            } catch (RestClientException rce) {
                LOG.error("client exception loading jobs details", rce);
                throw rce;
            } catch (URISyntaxException e1) {
                LOG.error("wrong syntax url for loading jobs details", e1);
            }
        }
        return result;
    }

    @SuppressWarnings({"PMD.NPathComplexity", "PMD.ExcessiveMethodLength", "PMD.AvoidBranchingStatementAsLastInLoop", "PMD.EmptyIfStmt"})
    private void getProjectDetails(String projectID, String projectName, String projectURL, String instanceUrl,
                                   Map<TeamcityProject, Map<jobData, Set<BaseModel>>> result) throws URISyntaxException, ParseException {
        LOG.debug("getProjectDetails: projectName " + projectName + " projectURL: " + projectURL);

        Map<jobData, Set<BaseModel>> jobDataMap = new HashMap();

        TeamcityProject teamcityProject = new TeamcityProject();
        teamcityProject.setInstanceUrl(instanceUrl);
        teamcityProject.setJobName(projectName);
        teamcityProject.setJobUrl(projectURL);
        teamcityProject.getOptions().put("projectId", projectID);

        Set<BaseModel> builds = getBuildDetailsForTeamcityProject(projectID, instanceUrl);

        jobDataMap.put(jobData.BUILD, builds);

        result.put(teamcityProject, jobDataMap);
    }


    private Set<BaseModel> getBuildDetailsForTeamcityProjectPaginated(String projectID, String instanceUrl, int startCount, int endCount) throws URISyntaxException, ParseException {
        Set<BaseModel> builds = new LinkedHashSet<>();
        try {
            String allBuildsUrl = joinURL(instanceUrl, new String[]{BUILD_DETAILS_URL_SUFFIX});
            LOG.info("Fetching builds for project {}", allBuildsUrl);
            String url = joinURL(allBuildsUrl, new String[]{String.format("?locator=project:%s,count:%d,start:%d", projectID, endCount, startCount)});
            ResponseEntity<String> responseEntity = makeRestCall(url);
            String returnJSON = responseEntity.getBody();
            if (StringUtils.isEmpty(returnJSON)) {
                return Collections.emptySet();
            }
            JSONParser parser = new JSONParser();
            JSONObject object = (JSONObject) parser.parse(returnJSON);

            if (object.isEmpty()) {
                return Collections.emptySet();
            }
            JSONArray jsonBuilds = getJsonArray(object, "build");
            for (Object build : jsonBuilds) {
                JSONObject jsonBuild = (JSONObject) build;
                // A basic Build object. This will be fleshed out later if this is a new Build.
                String buildID = jsonBuild.get("id").toString();
                String buildNumber = jsonBuild.get("number").toString();
                LOG.debug(" buildNumber: " + buildNumber);
                Build teamcityBuild = new Build();
                teamcityBuild.setNumber(buildNumber);
                String buildURL = String.format("%s?locator=id:%s", allBuildsUrl, buildID); //String buildURL = getString(jsonBuild, "webUrl");
                LOG.debug(" Adding Build: " + buildURL);
                teamcityBuild.setBuildUrl(buildURL);
                teamcityBuild.setBuildStatus(getBuildStatus(jsonBuild));
                builds.add(teamcityBuild);
            }
        } catch (HttpClientErrorException hce) {
            LOG.error("http client exception loading build details", hce);
        }
        return builds;

    }

    private Set<BaseModel> getBuildDetailsForTeamcityProject(String projectID, String instanceUrl) throws URISyntaxException, ParseException {
        Set<BaseModel> allBuilds = new LinkedHashSet<>();
        int startCount = 0;
        int endCount = 100;
        while (true) {
            Set<BaseModel> builds = getBuildDetailsForTeamcityProjectPaginated(projectID, instanceUrl, startCount, endCount);
            if (builds.isEmpty()) {
                break;
            }
            allBuilds.addAll(builds);
            startCount += 100;
//            endCount = endCount + 100;
        }
        return allBuilds;
    }


    @Override
    public Build getBuildDetails(String buildUrl, String instanceUrl) {
        String formattedBuildUrl = formatBuildUrl(buildUrl);

        try {
            LOG.debug("buildUrl: " + buildUrl);
            LOG.debug("formattedBuildUrl: " + formattedBuildUrl);
            String url = rebuildJobUrl(formattedBuildUrl, instanceUrl);
            ResponseEntity<String> result = makeRestCall(url);
            String resultJSON = result.getBody();
            if (StringUtils.isEmpty(resultJSON)) {
                LOG.error("Error getting build details for. URL=" + url);
                return null;
            }
            JSONParser parser = new JSONParser();
            try {
                JSONObject buildJson = (JSONObject) parser.parse(resultJSON);
//                JSONObject buildDetails = (JSONObject) ((JSONArray) buildJson.get("build")).get(0);
                String buildStatus = buildJson.get("state").toString();
                // Ignore jobs that are building
                if (buildStatus != "finished") {
                    Build build = new Build();
                    long startTime = getTimeInMillis(buildJson.get("startDate").toString());
                    long endTime = getTimeInMillis(buildJson.get("finishDate").toString());
                    long duration = endTime - startTime;
                    build.setNumber(buildJson.get("id").toString());
                    build.setBuildUrl(formattedBuildUrl);
                    build.setTimestamp(System.currentTimeMillis());
                    build.setStartTime(startTime);
                    build.setEndTime(endTime);
                    build.setDuration(duration);
                    build.setBuildStatus(getBuildStatus(buildJson));

                    //For git SCM, add the repoBranches. For other SCM types, it's handled while adding changesets
                    build.getCodeRepos().addAll(getGitRepoBranch(buildJson));


                    // Need to handle duplicate changesets bug in Pipeline jobs (https://issues.jenkins-ci.org/browse/JENKINS-40352)
                    Set<String> commitIds = new HashSet<>();
                    // This is empty for git
                    Set<String> revisions = new HashSet<>();

                    JSONObject changeSet = (JSONObject) buildJson.get("changeSet");
                    if (changeSet != null) {
                        addChangeSet(build, changeSet, commitIds, revisions);
                    }
                    return build;
                }

            } catch (ParseException e) {
                LOG.error("Parsing build: " + formattedBuildUrl, e);
            }
        } catch (RestClientException rce) {
            LOG.error("Client exception loading build details: " + rce.getMessage() + ". URL =" + formattedBuildUrl);
        } catch (MalformedURLException mfe) {
            LOG.error("Malformed url for loading build details" + mfe.getMessage() + ". URL =" + formattedBuildUrl);
        } catch (URISyntaxException use) {
            LOG.error("Uri syntax exception for loading build details" + use.getMessage() + ". URL =" + formattedBuildUrl);
        } catch (RuntimeException re) {
            LOG.error("Unknown error in getting build details. URL=" + formattedBuildUrl, re);
        } catch (UnsupportedEncodingException unse) {
            LOG.error("Unsupported Encoding Exception in getting build details. URL=" + formattedBuildUrl, unse);
        }
        LOG.debug("finished build");
        return null;
    }

    private long getTimeInMillis(String startDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String dateWithoutOffset = startDate.substring(0,15);
        String offset = startDate.substring(15);
        LocalDateTime formattedDateTime = LocalDateTime.parse(dateWithoutOffset, formatter);
        String formattedOffset = offset.substring(0,3) + ":" + offset.substring(3);
        ZoneOffset zoneOffset = ZoneOffset.of(formattedOffset);
        return formattedDateTime.atOffset(zoneOffset).toEpochSecond()*1000;
    }

    //to get all the details (time, started, end)
    private String formatBuildUrl(String buildUrl) {
       return buildUrl.split("\\?")[0] + "/" + buildUrl.split("=")[1];
    }

    //This method will rebuild the API endpoint because the buildUrl obtained via Jenkins API
    //does not save the auth user info and we need to add it back.
    public static String rebuildJobUrl(String build, String server) throws URISyntaxException, MalformedURLException, UnsupportedEncodingException {
        URL instanceUrl = new URL(server);
        String userInfo = instanceUrl.getUserInfo();
        String instanceProtocol = instanceUrl.getProtocol();

        //decode to handle + in the job name.
        String buildEscapeChar = build.replace("+", "%2B");

        //decode to handle spaces in the job name.
        URL buildUrl = new URL(URLDecoder.decode(buildEscapeChar, "UTF-8"));
        String buildPath = buildUrl.getPath();

        String host = buildUrl.getHost();
        int port = buildUrl.getPort();
        URI newUri = new URI(instanceProtocol, userInfo, host, port, buildPath, null, null);
        return newUri.toString();
    }


    /**
     * Grabs changeset information for the given build.
     *
     * @param build     a Build
     * @param changeSet the build JSON object
     * @param commitIds the commitIds
     * @param revisions the revisions
     */
    private void addChangeSet(Build build, JSONObject changeSet, Set<String> commitIds, Set<String> revisions) {
        String scmType = getString(changeSet, "kind");
        Map<String, RepoBranch> revisionToUrl = new HashMap<>();

        // Build a map of revision to module (scm url). This is not always
        // provided by the Hudson API, but we can use it if available.
        // For git, this map is empty.
        for (Object revision : getJsonArray(changeSet, "revisions")) {
            JSONObject json = (JSONObject) revision;
            String revisionId = json.get("revision").toString();
            if (StringUtils.isNotEmpty(revisionId) && !revisions.contains(revisionId)) {
                RepoBranch rb = new RepoBranch();
                rb.setUrl(getString(json, "module"));
                rb.setType(RepoBranch.RepoType.fromString(scmType));
                revisionToUrl.put(revisionId, rb);
                build.getCodeRepos().add(rb);
            }
        }

        for (Object item : getJsonArray(changeSet, "items")) {
            JSONObject jsonItem = (JSONObject) item;
            String commitId = getRevision(jsonItem);
            if (StringUtils.isNotEmpty(commitId) && !commitIds.contains(commitId)) {
                SCM scm = new SCM();
                scm.setScmAuthor(getCommitAuthor(jsonItem));
                scm.setScmCommitLog(getString(jsonItem, "msg"));
                scm.setScmCommitTimestamp(getCommitTimestamp(jsonItem));
                scm.setScmRevisionNumber(commitId);
                RepoBranch repoBranch = revisionToUrl.get(scm.getScmRevisionNumber());
                if (repoBranch != null) {
                    scm.setScmUrl(repoBranch.getUrl());
                    scm.setScmBranch(repoBranch.getBranch());
                }

                scm.setNumberOfChanges(getJsonArray(jsonItem, "paths").size());
                build.getSourceChangeSet().add(scm);
                commitIds.add(commitId);
            }
        }
    }

    /**
     * Gathers repo urls, and the branch name from the last built revision.
     * Filters out the qualifiers from the branch name and sets the unqualified branch name.
     * We assume that all branches are in remotes/origin.
     */

    @SuppressWarnings("PMD")
    private List<RepoBranch> getGitRepoBranch(JSONObject buildJson) {
        List<RepoBranch> list = new ArrayList<>();

        JSONArray actions = getJsonArray(buildJson, "actions");
        for (Object action : actions) {
            JSONObject jsonAction = (JSONObject) action;
            if (jsonAction.size() > 0) {
                JSONObject lastBuiltRevision = null;
                JSONArray branches = null;
                JSONArray remoteUrls = getJsonArray((JSONObject) action, "remoteUrls");
                if (!remoteUrls.isEmpty()) {
                    lastBuiltRevision = (JSONObject) jsonAction.get("lastBuiltRevision");
                }
                if (lastBuiltRevision != null) {
                    branches = getJsonArray(lastBuiltRevision, "branch");
                }
                // As of git plugin 3.0.0, when multiple repos are configured in the git plugin itself instead of MultiSCM plugin,
                // they are stored unordered in a HashSet. So it's buggy and we cannot associate the correct branch information.
                // So for now, we loop through all the remoteUrls and associate the built branch(es) with all of them.
                if (branches != null && !branches.isEmpty()) {
                    for (Object url : remoteUrls) {
                        String sUrl = (String) url;
                        if (sUrl != null && !sUrl.isEmpty()) {
                            sUrl = removeGitExtensionFromUrl(sUrl);
                            for (Object branchObj : branches) {
                                String branchName = getString((JSONObject) branchObj, "name");
                                if (branchName != null) {
                                    String unqualifiedBranchName = getUnqualifiedBranch(branchName);
                                    RepoBranch grb = new RepoBranch(sUrl, unqualifiedBranchName, RepoBranch.RepoType.GIT);
                                    list.add(grb);
                                }
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    private String removeGitExtensionFromUrl(String url) {
        String sUrl = url;
        //remove .git from the urls
        if (sUrl.endsWith(".git")) {
            sUrl = sUrl.substring(0, sUrl.lastIndexOf(".git"));
        }
        return sUrl;
    }

    /**
     * Gets the unqualified branch name given the qualified one of the following forms:
     * 1. refs/remotes/<remote name>/<branch name>
     * 2. remotes/<remote name>/<branch name>
     * 3. origin/<branch name>
     * 4. <branch name>
     *
     * @param qualifiedBranch
     * @return the unqualified branch name
     */

    private String getUnqualifiedBranch(String qualifiedBranch) {
        String branchName = qualifiedBranch;
        Pattern pattern = Pattern.compile("(refs/)?remotes/[^/]+/(.*)|(origin[0-9]*/)?(.*)");
        Matcher matcher = pattern.matcher(branchName);
        if (matcher.matches()) {
            if (matcher.group(2) != null) {
                branchName = matcher.group(2);
            } else if (matcher.group(4) != null) {
                branchName = matcher.group(4);
            }
        }
        return branchName;
    }

    private long getCommitTimestamp(JSONObject jsonItem) {
        if (jsonItem.get("timestamp") != null) {
            return (Long) jsonItem.get("timestamp");
        } else if (jsonItem.get("date") != null) {
            String dateString = (String) jsonItem.get("date");
            try {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateString).getTime();
            } catch (java.text.ParseException e) {
                // Try an alternate date format...looks like this one is used by Git
                try {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(dateString).getTime();
                } catch (java.text.ParseException e1) {
                    LOG.error("Invalid date string: " + dateString, e);
                }
            }
        }
        return 0;
    }

    private String getString(JSONObject json, String key) {
        return (String) json.get(key);
    }

    private Boolean getBoolean(JSONObject json, String key) {
        return (Boolean) json.get(key);
    }

    private long timestamp(JSONObject json, String key) {
        Object obj = json.get(key);
        if (obj != null) {
            try {
                return new SimpleDateFormat(DATE_FORMAT).parse(obj.toString()).getTime();
            } catch (java.text.ParseException e) {
                LOG.warn(obj + " is not in expected format " + DATE_FORMAT + e);
            }
        }
        return 0;
    }

    private String getRevision(JSONObject jsonItem) {
        // Use revision if provided, otherwise use id
        Long revision = (Long) jsonItem.get("revision");
        return revision == null ? getString(jsonItem, "id") : revision.toString();
    }

    private JSONArray getJsonArray(JSONObject json, String key) {
        Object array = json.get(key);
        return array == null ? new JSONArray() : (JSONArray) array;
    }

    private String firstCulprit(JSONObject buildJson) {
        JSONArray culprits = getJsonArray(buildJson, "culprits");
        if (CollectionUtils.isEmpty(culprits)) {
            return null;
        }
        JSONObject culprit = (JSONObject) culprits.get(0);
        return getFullName(culprit);
    }

    private String getFullName(JSONObject author) {
        return getString(author, "fullName");
    }

    private String getCommitAuthor(JSONObject jsonItem) {
        // Use user if provided, otherwise use author.fullName
        JSONObject author = (JSONObject) jsonItem.get("author");
        return author == null ? getString(jsonItem, "user") : getFullName(author);
    }

    private BuildStatus getBuildStatus(JSONObject buildJson) {
        String status = buildJson.get("status").toString();
        switch (status) {
            case "SUCCESS":
                return BuildStatus.Success;
            case "UNSTABLE":
                return BuildStatus.Unstable;
            case "FAILURE":
                return BuildStatus.Failure;
            case "ABORTED":
                return BuildStatus.Aborted;
            default:
                return BuildStatus.Unknown;
        }
    }

    @SuppressWarnings("PMD")
    protected ResponseEntity<String> makeRestCall(String sUrl) throws URISyntaxException {
        LOG.debug("Enter makeRestCall " + sUrl);
        URI thisuri = URI.create(sUrl);
        String userInfo = thisuri.getUserInfo();

        //get userinfo from URI or settings (in spring properties)
        if (StringUtils.isEmpty(userInfo)) {
            List<String> servers = this.settings.getServers();
            List<String> usernames = this.settings.getUsernames();
            List<String> apiKeys = this.settings.getApiKeys();
            if (CollectionUtils.isNotEmpty(servers) && CollectionUtils.isNotEmpty(usernames) && CollectionUtils.isNotEmpty(apiKeys)) {
                boolean exactMatchFound = false;
                for (int i = 0; i < servers.size(); i++) {
                    if ((servers.get(i) != null)) {
                        String domain1 = getDomain(sUrl);
                        String domain2 = getDomain(servers.get(i));
                        if (StringUtils.isNotEmpty(domain1) && StringUtils.isNotEmpty(domain2) && Objects.equals(domain1, domain2)
                                && getPort(sUrl) == getPort(servers.get(i))) {
                            exactMatchFound = true;
                        }
                        if (exactMatchFound && (i < usernames.size()) && (i < apiKeys.size())
                                && (StringUtils.isNotEmpty(usernames.get(i))) && (StringUtils.isNotEmpty(apiKeys.get(i)))) {
                            userInfo = usernames.get(i) + ":" + apiKeys.get(i);
                        }
                        if (exactMatchFound) {
                            break;
                        }
                    }
                }
                if (!exactMatchFound) {
                    LOG.warn("Credentials for the following url was not found. This could happen if the domain/subdomain/IP address "
                            + "in the build url returned by Jenkins and the Jenkins instance url in your Hygieia configuration do not match: "
                            + "\"" + sUrl + "\"");
                }
            }
        }
        // Basic Auth only.
        if (StringUtils.isNotEmpty(userInfo)) {
            LOG.debug("make rest call with headers");
            LOG.debug("hitting url:" + thisuri);
            return rest.exchange(thisuri, HttpMethod.GET,
                    new HttpEntity<>(createHeaders(userInfo)),
                    String.class);
        } else {
            LOG.debug("make rest call without headers");
            return rest.exchange(thisuri, HttpMethod.GET, null,
                    String.class);
        }

    }

    private String getDomain(String url) throws URISyntaxException {
        URI uri = new URI(url);
        return uri.getHost();
    }

    private int getPort(String url) throws URISyntaxException {
        URI uri = new URI(url);
        return uri.getPort();
    }

    protected HttpHeaders createHeaders(final String userInfo) {
        byte[] encodedAuth = Base64.encodeBase64(
                userInfo.getBytes(StandardCharsets.US_ASCII));
        String authHeader = "Basic " + new String(encodedAuth);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        return headers;
    }


    // join a base url to another path or paths - this will handle trailing or non-trailing /'s
    public static String joinURL(String base, String[] paths) {
        StringBuilder result = new StringBuilder(base);
        Arrays.stream(paths).map(path -> path.replaceFirst("^(\\/)+", "")).forEach(p -> {
            if (result.lastIndexOf("/") != result.length() - 1) {
                result.append('/');
            }
            result.append(p);
        });
        return result.toString();
    }
}
