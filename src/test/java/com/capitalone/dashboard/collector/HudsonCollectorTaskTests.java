package com.capitalone.dashboard.collector;
//
//import com.capitalone.dashboard.model.BaseModel;
//import com.capitalone.dashboard.model.Build;
//import com.capitalone.dashboard.model.Component;
//import com.capitalone.dashboard.model.TeamcityCollector;
//import com.capitalone.dashboard.model.TeamcityJob;
//import com.capitalone.dashboard.repository.BuildRepository;
//import com.capitalone.dashboard.repository.ComponentRepository;
//import com.capitalone.dashboard.repository.TeamcityCollectorRepository;
//import com.capitalone.dashboard.repository.TeamcityJobRepository;
//import com.google.common.collect.Sets;
//import org.bson.types.ObjectId;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.runners.MockitoJUnitRunner;
//import org.springframework.scheduling.TaskScheduler;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import static org.mockito.Matchers.anyListOf;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoMoreInteractions;
//import static org.mockito.Mockito.verifyZeroInteractions;
//import static org.mockito.Mockito.when;
//
//@RunWith(MockitoJUnitRunner.class)
//public class HudsonCollectorTaskTests {
//
//    @Mock
//    private TaskScheduler taskScheduler;
//    @Mock
//    private TeamcityCollectorRepository hudsonCollectorRepository;
//    @Mock
//    private TeamcityJobRepository hudsonJobRepository;
//    @Mock
//    private BuildRepository buildRepository;
//    @Mock
//    private TeamcityClient hudsonClient;
//    @Mock
//    private TeamcitySettings hudsonSettings;
//    @Mock
//    private ComponentRepository dbComponentRepository;
//
//    @InjectMocks
//    private TeamcityCollectorTask task;
//
//    private static final String SERVER1 = "server1";
//    private static final String NICENAME1 = "niceName1";
//    private static final String ENVIONMENT1 = "DEV";
//
//    @Test
//    public void collect_noBuildServers_nothingAdded() {
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        task.collect(new TeamcityCollector());
//        verifyZeroInteractions(hudsonClient, buildRepository);
//    }
//
//    @Test
//    public void collect_noJobsOnServer_nothingAdded() {
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(new HashMap<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>>());
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        task.collect(collectorWithOneServer());
//
//        verify(hudsonClient).getInstanceJobs(SERVER1);
//        verifyNoMoreInteractions(hudsonClient, buildRepository);
//    }
//
//    @Test
//    public void collect_twoJobs_jobsAdded() {
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(twoJobsWithTwoBuilds(SERVER1, NICENAME1));
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        List<TeamcityJob> hudsonJobs = new ArrayList<>();
//        TeamcityJob hudsonJob = hudsonJob("1", SERVER1, "JOB1_URL", NICENAME1);
//        hudsonJobs.add(hudsonJob);
//        when(hudsonJobRepository.findEnabledJobs(null, "server1")).thenReturn(hudsonJobs);
//        task.collect(collectorWithOneServer());
//        verify(hudsonJobRepository, times(1)).save(anyListOf(TeamcityJob.class));
//    }
//
//    @Test
//    public void collect_twoJobs_jobsAdded_random_order() {
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(twoJobsWithTwoBuilds(SERVER1, NICENAME1));
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        List<TeamcityJob> hudsonJobs = new ArrayList<>();
//        TeamcityJob hudsonJob = hudsonJob("2", SERVER1, "JOB2_URL", NICENAME1);
//        hudsonJobs.add(hudsonJob);
//        when(hudsonJobRepository.findEnabledJobs(null, "server1")).thenReturn(hudsonJobs);
//        task.collect(collectorWithOneServer());
//        verify(hudsonJobRepository, times(1)).save(anyListOf(TeamcityJob.class));
//    }
//
//
//    @Test
//    public void collect_oneJob_exists_notAdded() {
//        TeamcityCollector collector = collectorWithOneServer();
//        TeamcityJob job = hudsonJob("1", SERVER1, "JOB1_URL", NICENAME1);
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(oneJobWithBuilds(job));
//        when(hudsonJobRepository.findJob(collector.getId(), SERVER1, job.getJobName()))
//                .thenReturn(job);
//        when(dbComponentRepository.findAll()).thenReturn(components());
//
//        task.collect(collector);
//
//        verify(hudsonJobRepository, never()).save(job);
//    }
//
//
//    @Test
//    public void delete_job() {
//        TeamcityCollector collector = collectorWithOneServer();
//        collector.setId(ObjectId.get());
//        TeamcityJob job1 = hudsonJob("1", SERVER1, "JOB1_URL", NICENAME1);
//        job1.setCollectorId(collector.getId());
//        TeamcityJob job2 = hudsonJob("2", SERVER1, "JOB2_URL", NICENAME1);
//        job2.setCollectorId(collector.getId());
//        List<TeamcityJob> jobs = new ArrayList<>();
//        jobs.add(job1);
//        jobs.add(job2);
//        Set<ObjectId> udId = new HashSet<>();
//        udId.add(collector.getId());
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(oneJobWithBuilds(job1));
//        when(hudsonJobRepository.findByCollectorIdIn(udId)).thenReturn(jobs);
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        task.collect(collector);
//        List<TeamcityJob> delete = new ArrayList<>();
//        delete.add(job2);
//        verify(hudsonJobRepository, times(1)).delete(delete);
//    }
//
//    @Test
//    public void delete_never_job() {
//        TeamcityCollector collector = collectorWithOneServer();
//        collector.setId(ObjectId.get());
//        TeamcityJob job1 = hudsonJob("1", SERVER1, "JOB1_URL", NICENAME1);
//        job1.setCollectorId(collector.getId());
//        List<TeamcityJob> jobs = new ArrayList<>();
//        jobs.add(job1);
//        Set<ObjectId> udId = new HashSet<>();
//        udId.add(collector.getId());
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(oneJobWithBuilds(job1));
//        when(hudsonJobRepository.findByCollectorIdIn(udId)).thenReturn(jobs);
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        task.collect(collector);
//        verify(hudsonJobRepository, never()).delete(anyListOf(TeamcityJob.class));
//    }
//
//    @Test
//    public void collect_jobNotEnabled_buildNotAdded() {
//        TeamcityCollector collector = collectorWithOneServer();
//        TeamcityJob job = hudsonJob("1", SERVER1, "JOB1_URL", NICENAME1);
//        Build build = build("1", "JOB1_1_URL");
//
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(oneJobWithBuilds(job, build));
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        task.collect(collector);
//
//        verify(buildRepository, never()).save(build);
//    }
//
//    @Test
//    public void collect_jobEnabled_buildExists_buildNotAdded() {
//        TeamcityCollector collector = collectorWithOneServer();
//        TeamcityJob job = hudsonJob("1", SERVER1, "JOB1_URL", NICENAME1);
//        Build build = build("1", "JOB1_1_URL");
//
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(oneJobWithBuilds(job, build));
//        when(hudsonJobRepository.findEnabledJobs(collector.getId(), SERVER1))
//                .thenReturn(Arrays.asList(job));
//        when(buildRepository.findByCollectorItemIdAndNumber(job.getId(), build.getNumber())).thenReturn(build);
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        task.collect(collector);
//
//        verify(buildRepository, never()).save(build);
//    }
//
//    @Test
//    public void collect_jobEnabled_newBuild_buildAdded() {
//        TeamcityCollector collector = collectorWithOneServer();
//        TeamcityJob job = hudsonJob("1", SERVER1, "JOB1_URL", NICENAME1);
//        Build build = build("1", "JOB1_1_URL");
//
//        when(hudsonClient.getInstanceJobs(SERVER1)).thenReturn(oneJobWithBuilds(job, build));
//        when(hudsonJobRepository.findEnabledJobs(collector.getId(), SERVER1))
//                .thenReturn(Arrays.asList(job));
//        when(buildRepository.findByCollectorItemIdAndNumber(job.getId(), build.getNumber())).thenReturn(null);
//        when(hudsonClient.getBuildDetails(build.getBuildUrl(), job.getInstanceUrl())).thenReturn(build);
//        when(dbComponentRepository.findAll()).thenReturn(components());
//        task.collect(collector);
//
//        verify(buildRepository, times(1)).save(build);
//    }
//
//    private TeamcityCollector collectorWithOneServer() {
//        return TeamcityCollector.prototype(Arrays.asList(SERVER1), Arrays.asList(NICENAME1), Arrays.asList(ENVIONMENT1));
//    }
//
//    private Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> oneJobWithBuilds(TeamcityJob job, Build... builds) {
//        Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> jobs = new HashMap<>();
//
//        Map<TeamcityClient.jobData, Set<BaseModel>> buildsMap = new HashMap<>();
//        buildsMap.put( TeamcityClient.jobData.BUILD, Sets.newHashSet(builds) );
//
//        jobs.put(job, buildsMap);
//        return jobs;
//    }
//
//    private Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> twoJobsWithTwoBuilds(String server, String niceName) {
//        Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> jobs = new HashMap<>();
//
//        Map<TeamcityClient.jobData, Set<BaseModel>> buildsMap = new HashMap<>();
//        buildsMap.put(TeamcityClient.jobData.BUILD, Sets.newHashSet(build("1", "JOB1_1_URL"), build("1", "JOB1_2_URL")));
//        buildsMap.put(TeamcityClient.jobData.BUILD, Sets.newHashSet(build("2", "JOB2_1_URL"), build("2", "JOB2_2_URL")));
//
//        jobs.put(hudsonJob("1", server, "JOB1_URL", niceName), buildsMap );
//        jobs.put(hudsonJob("2", server, "JOB2_URL", niceName), buildsMap );
//        return jobs;
//    }
//
//    private Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> twoJobsWithTwoBuildsRandom(String server, String niceName) {
//        Map<TeamcityJob, Map<TeamcityClient.jobData, Set<BaseModel>>> jobs = new HashMap<>();
//
//        Map<TeamcityClient.jobData, Set<BaseModel>> buildsMap = new HashMap<>();
//        buildsMap.put(TeamcityClient.jobData.BUILD, Sets.newHashSet(build("2", "JOB2_1_URL"), build("2", "JOB2_2_URL")));
//        buildsMap.put(TeamcityClient.jobData.BUILD, Sets.newHashSet(build("1", "JOB1_1_URL"), build("1", "JOB1_2_URL")));
//
//        jobs.put(hudsonJob("2", server, "JOB2_URL", niceName), buildsMap );
//        jobs.put(hudsonJob("1", server, "JOB1_URL", niceName), buildsMap );
//        return jobs;
//    }
//
//    private TeamcityJob hudsonJob(String jobName, String instanceUrl, String jobUrl, String niceName) {
//        TeamcityJob job = new TeamcityJob();
//        job.setJobName(jobName);
//        job.setInstanceUrl(instanceUrl);
//        job.setJobUrl(jobUrl);
//        job.setNiceName(niceName);
//        return job;
//    }
//
//    private Build build(String number, String url) {
//        Build build = new Build();
//        build.setNumber(number);
//        build.setBuildUrl(url);
//        return build;
//    }
//
//    private ArrayList<com.capitalone.dashboard.model.Component> components() {
//        ArrayList<com.capitalone.dashboard.model.Component> cArray = new ArrayList<com.capitalone.dashboard.model.Component>();
//        com.capitalone.dashboard.model.Component c = new Component();
//        c.setId(new ObjectId());
//        c.setName("COMPONENT1");
//        c.setOwner("JOHN");
//        cArray.add(c);
//        return cArray;
//    }
//}
