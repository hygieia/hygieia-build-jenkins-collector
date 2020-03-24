package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.repository.*;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;

import java.util.*;


/**
 * CollectorTask that fetches Build information from Hudson
 */
@Component
public class TeamcityCollectorTask extends CollectorTask<TeamcityCollector> {
    @SuppressWarnings("PMD.UnusedPrivateField")
//    private static final Log LOG = LogFactory.getLog(HudsonCollectorTask.class);

    private final TeamcityCollectorRepository teamcityCollectorRepository;
    private final TeamcityJobRepository teamcityJobRepository;
    private final BuildRepository buildRepository;
    private final CollItemConfigHistoryRepository configRepository;
    private final TeamcityClient teamcityClient;
    private final TeamcitySettings teamcitySettings;
    private final ComponentRepository dbComponentRepository;
	private final ConfigurationRepository configurationRepository;

    @Autowired
    public TeamcityCollectorTask(TaskScheduler taskScheduler,
                                 TeamcityCollectorRepository teamcityCollectorRepository,
                                 TeamcityJobRepository teamcityJobRepository,
                                 BuildRepository buildRepository, CollItemConfigHistoryRepository configRepository, TeamcityClient teamcityClient,
                                 TeamcitySettings teamcitySettings,
                                 ComponentRepository dbComponentRepository,
                                 ConfigurationRepository configurationRepository) {
        super(taskScheduler, "Teamcity");
        this.teamcityCollectorRepository = teamcityCollectorRepository;
        this.teamcityJobRepository = teamcityJobRepository;
        this.buildRepository = buildRepository;
        this.configRepository = configRepository;
        this.teamcityClient = teamcityClient;
        this.teamcitySettings = teamcitySettings;
        this.dbComponentRepository = dbComponentRepository;
		this.configurationRepository = configurationRepository;
    }

    @Override
    public TeamcityCollector getCollector() {
    	Configuration config = configurationRepository.findByCollectorName("Teamcity");
        // Only use Admin Page Jenkins server configuration when available
        // otherwise use properties file Jenkins server configuration
        if (config != null ) {
			config.decryptOrEncrptInfo();
			// To clear the username and password from existing run and
			// pick the latest
            teamcitySettings.getUsernames().clear();
            teamcitySettings.getServers().clear();
            teamcitySettings.getApiKeys().clear();
			for (Map<String, String> TeamcityServer : config.getInfo()) {
				teamcitySettings.getServers().add(TeamcityServer.get("url"));
				teamcitySettings.getUsernames().add(TeamcityServer.get("userName"));
				teamcitySettings.getApiKeys().add(TeamcityServer.get("password"));
			}
		}
        return TeamcityCollector.prototype(teamcitySettings.getServers(), teamcitySettings.getNiceNames(),
                teamcitySettings.getEnvironments());
    }

    @Override
    public BaseCollectorRepository<TeamcityCollector> getCollectorRepository() {
        return teamcityCollectorRepository;
    }

    @Override
    public String getCron() {
        return teamcitySettings.getCron();
    }

    @Override
    public void collect(TeamcityCollector collector) {
        long start = System.currentTimeMillis();
        Set<ObjectId> udId = new HashSet<>();
        udId.add(collector.getId());
        List<TeamcityJob> existingJobs = teamcityJobRepository.findByCollectorIdIn(udId);
        List<TeamcityJob> activeJobs = new ArrayList<>();
        List<String> activeServers = new ArrayList<>();
        activeServers.addAll(collector.getBuildServers());

        clean(collector, existingJobs);

        for (String instanceUrl : collector.getBuildServers()) {
            logBanner(instanceUrl);
            try {
                Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> dataByJob = teamcityClient
                        .getInstanceJobs(instanceUrl);
                log("Fetched jobs", start);
                activeJobs.addAll(dataByJob.keySet());
                addNewJobs(dataByJob.keySet(), existingJobs, collector);
                addNewBuilds(enabledJobs(collector, instanceUrl), dataByJob);
                addNewConfigs(enabledJobs(collector, instanceUrl), dataByJob);
                log("Finished", start);
            } catch (RestClientException rce) {
                activeServers.remove(instanceUrl); // since it was a rest exception, we will not delete this job  and wait for
                // rest exceptions to clear up at a later run.
                log("Error getting jobs for: " + instanceUrl, start);
            }
        }
        // Delete jobs that will be no longer collected because servers have moved etc.
        deleteUnwantedJobs(activeJobs, existingJobs, activeServers, collector);
    }

    /**
     * Clean up unused hudson/jenkins collector items
     *
     * @param collector    the {@link TeamcityCollector}
     * @param existingJobs
     */

    private void clean(TeamcityCollector collector, List<TeamcityJob> existingJobs) {
        Set<ObjectId> uniqueIDs = new HashSet<>();
        for (com.capitalone.dashboard.model.Component comp : dbComponentRepository
                .findAll()) {

            if (CollectionUtils.isEmpty(comp.getCollectorItems())) continue;

            List<CollectorItem> itemList = comp.getCollectorItems().get(CollectorType.Build);

            if (CollectionUtils.isEmpty(itemList)) continue;

            for (CollectorItem ci : itemList) {
                if (collector.getId().equals(ci.getCollectorId())) {
                    uniqueIDs.add(ci.getId());
                }
            }
        }
        List<TeamcityJob> stateChangeJobList = new ArrayList<>();
        for (TeamcityJob job : existingJobs) {
            if ((job.isEnabled() && !uniqueIDs.contains(job.getId())) ||  // if it was enabled but not on a dashboard
                    (!job.isEnabled() && uniqueIDs.contains(job.getId()))) { // OR it was disabled and now on a dashboard
                job.setEnabled(uniqueIDs.contains(job.getId()));
                stateChangeJobList.add(job);
            }
        }
        if (!CollectionUtils.isEmpty(stateChangeJobList)) {
            teamcityJobRepository.save(stateChangeJobList);
        }
    }

    /**
     * Delete orphaned job collector items
     *
     * @param activeJobs
     * @param existingJobs
     * @param activeServers
     * @param collector
     */
    private void deleteUnwantedJobs(List<TeamcityJob> activeJobs, List<TeamcityJob> existingJobs, List<String> activeServers, TeamcityCollector collector) {

        List<TeamcityJob> deleteJobList = new ArrayList<>();
        for (TeamcityJob job : existingJobs) {
            if (job.isPushed()) continue; // build servers that push jobs will not be in active servers list by design

            // if we have a collector item for the job in repository but it's build server is not what we collect, remove it.
            if (!collector.getBuildServers().contains(job.getInstanceUrl())) {
                deleteJobList.add(job);
            }

            //if the collector id of the collector item for the job in the repo does not match with the collector ID, delete it.
            if (!Objects.equals(job.getCollectorId(), collector.getId())) {
                deleteJobList.add(job);
            }

            // this is to handle jobs that have been deleted from build servers. Will get 404 if we don't delete them.
            if (activeServers.contains(job.getInstanceUrl()) && !activeJobs.contains(job)) {
                deleteJobList.add(job);
            }

        }
        if (!CollectionUtils.isEmpty(deleteJobList)) {
            teamcityJobRepository.delete(deleteJobList);
        }
    }

    /**
     * Iterates over the enabled build jobs and adds new builds to the database.
     *
     * @param enabledJobs list of enabled {@link TeamcityJob}s
     * @param dataByJob maps a {@link TeamcityJob} to a map of data with {@link Build}s.
     */
    private void addNewBuilds(List<TeamcityJob> enabledJobs,
                              Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> dataByJob) {
        long start = System.currentTimeMillis();
        int count = 0;

        for (TeamcityJob job : enabledJobs) {
            if (job.isPushed()) continue;
            // process new builds in the order of their build numbers - this has implication to handling of commits in BuildEventListener

            Map<TeamcityClient.jobData, Set<BaseModel>> jobDataSetMap = dataByJob.get(job);
            if (jobDataSetMap == null) {
                continue;
            }
            Set<BaseModel> buildsSet = jobDataSetMap.get(TeamcityClient.jobData.BUILD);

            ArrayList<BaseModel> builds = Lists.newArrayList(nullSafe(buildsSet));

            builds.sort(Comparator.comparingInt(b -> Integer.valueOf(((Build) b).getNumber())));
            for (BaseModel buildSummary : builds) {
                if (isNewBuild(job, (Build)buildSummary)) {
                    Build build = teamcityClient.getBuildDetails(((Build)buildSummary)
                            .getBuildUrl(), job.getInstanceUrl());
                    job.setLastUpdated(System.currentTimeMillis());
                    teamcityJobRepository.save(job);
                    if (build != null) {
                        build.setCollectorItemId(job.getId());
                        buildRepository.save(build);
                        count++;
                    }
                }
            }
        }
        log("New builds", start, count);
    }

    private void addNewConfigs(List<TeamcityJob> enabledJobs,
                              Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> dataByJob) {
        long start = System.currentTimeMillis();
        int count = 0;

        for (TeamcityJob job : enabledJobs) {
            if (job.isPushed()) continue;
            // process new builds in the order of their build numbers - this has implication to handling of commits in BuildEventListener

            Map<TeamcityClient.jobData, Set<BaseModel>> jobDataSetMap = dataByJob.get(job);
            if (jobDataSetMap == null) {
                continue;
            }
            Set<BaseModel> configsSet = jobDataSetMap.get(TeamcityClient.jobData.CONFIG);

            ArrayList<BaseModel> configs = Lists.newArrayList(nullSafe(configsSet));

            configs.sort(Comparator.comparing(b -> new Date(((CollectorItemConfigHistory) b).getTimestamp())));

            for (BaseModel config : configs) {
                if (config != null && isNewConfig(job, (CollectorItemConfigHistory)config)) {
                    job.setLastUpdated(System.currentTimeMillis());
                    teamcityJobRepository.save(job);
                    ((CollectorItemConfigHistory)config).setCollectorItemId(job.getId());
                    configRepository.save((CollectorItemConfigHistory)config);
                    count++;
                }
            }
        }
        log("New configs", start, count);
    }

    private Set<BaseModel> nullSafe(Set<BaseModel> builds) {
        return builds == null ? new HashSet<>() : builds;
    }

    /**
     * Adds new {@link TeamcityJob}s to the database as disabled jobs.
     *
     * @param jobs         list of {@link TeamcityJob}s
     * @param existingJobs
     * @param collector    the {@link TeamcityCollector}
     */
    private void addNewJobs(Set<TeamcityJob> jobs, List<TeamcityJob> existingJobs, TeamcityCollector collector) {
        long start = System.currentTimeMillis();
        int count = 0;

        List<TeamcityJob> newJobs = new ArrayList<>();
        for (TeamcityJob job : jobs) {
            TeamcityJob existing = null;
            if (!CollectionUtils.isEmpty(existingJobs) && (existingJobs.contains(job))) {
                existing = existingJobs.get(existingJobs.indexOf(job));
            }

            String niceName = getNiceName(job, collector);
            String environment = getEnvironment(job, collector);
            if (existing == null) {
                job.setCollectorId(collector.getId());
                job.setEnabled(false); // Do not enable for collection. Will be enabled when added to dashboard
                job.setDescription(job.getJobName());
                if (StringUtils.isNotEmpty(niceName)) {
                    job.setNiceName(niceName);
                }
                if (StringUtils.isNotEmpty(environment)) {
                    job.setEnvironment(environment);
                }
                newJobs.add(job);
                count++;
            } else {
                if (StringUtils.isEmpty(existing.getNiceName()) && StringUtils.isNotEmpty(niceName)) {
                    existing.setNiceName(niceName);
                    teamcityJobRepository.save(existing);
                }
                if (StringUtils.isEmpty(existing.getEnvironment()) && StringUtils.isNotEmpty(environment)) {
                    existing.setEnvironment(environment);
                    teamcityJobRepository.save(existing);
                }
                if (StringUtils.isEmpty(existing.getInstanceUrl())) {
                    existing.setInstanceUrl(job.getInstanceUrl());
                    teamcityJobRepository.save(existing);
                }
            }
        }
        //save all in one shot
        if (!CollectionUtils.isEmpty(newJobs)) {
            teamcityJobRepository.save(newJobs);
        }
        log("New jobs", start, count);
    }

    private String getNiceName(TeamcityJob job, TeamcityCollector collector) {
        if (CollectionUtils.isEmpty(collector.getBuildServers())) return "";
        List<String> servers = collector.getBuildServers();
        List<String> niceNames = collector.getNiceNames();
        if (CollectionUtils.isEmpty(niceNames)) return "";
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).equalsIgnoreCase(job.getInstanceUrl()) && (niceNames.size() > i)) {
                return niceNames.get(i);
            }
        }
        return "";
    }

    private String getEnvironment(TeamcityJob job, TeamcityCollector collector) {
        if (CollectionUtils.isEmpty(collector.getBuildServers())) return "";
        List<String> servers = collector.getBuildServers();
        List<String> environments = collector.getEnvironments();
        if (CollectionUtils.isEmpty(environments)) return "";
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).equalsIgnoreCase(job.getInstanceUrl()) && (environments.size() > i)) {
                return environments.get(i);
            }
        }
        return "";
    }

    private List<TeamcityJob> enabledJobs(TeamcityCollector collector,
                                          String instanceUrl) {
        return teamcityJobRepository.findEnabledJobs(collector.getId(),
                instanceUrl);
    }

    @SuppressWarnings("unused")
    private TeamcityJob getExistingJob(TeamcityCollector collector, TeamcityJob job) {
        return teamcityJobRepository.findJob(collector.getId(),
                job.getInstanceUrl(), job.getJobName());
    }

    private boolean isNewBuild(TeamcityJob job, Build build) {
        return buildRepository.findByCollectorItemIdAndNumber(job.getId(),
                build.getNumber()) == null;
    }

    private boolean isNewConfig(TeamcityJob job, CollectorItemConfigHistory config) {
        return configRepository.findByCollectorItemIdAndTimestamp(job.getId(),config.getTimestamp()) == null;
    }
}
