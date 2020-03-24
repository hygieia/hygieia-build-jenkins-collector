package com.capitalone.dashboard.model;

import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extension of Collector that stores current build server configuration.
 */
public class TeamcityCollector extends Collector {
    private List<String> buildServers = new ArrayList<>();
    private List<String> niceNames = new ArrayList<>();
    private List<String> environments = new ArrayList<>();
    private static final String NICE_NAME = "niceName";
    private static final String JOB_NAME = "options.jobName";


    public List<String> getBuildServers() {
        return buildServers;
    }

    public List<String> getNiceNames() {
        return niceNames;
    }

    public void setNiceNames(List<String> niceNames) {
        this.niceNames = niceNames;
    }

    public List<String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<String> environments) {
        this.environments = environments;
    }

    public void setBuildServers(List<String> buildServers) {
        this.buildServers = buildServers;
    }

    public static TeamcityCollector prototype(List<String> buildServers, List<String> niceNames,
                                              List<String> environments) {
        TeamcityCollector protoType = new TeamcityCollector();
        protoType.setName("Hudson");
        protoType.setCollectorType(CollectorType.Build);
        protoType.setOnline(true);
        protoType.setEnabled(true);
        protoType.getBuildServers().addAll(buildServers);
        if (!CollectionUtils.isEmpty(niceNames)) {
            protoType.getNiceNames().addAll(niceNames);
        }
        if (!CollectionUtils.isEmpty(environments)) {
            protoType.getEnvironments().addAll(environments);
        }
        Map<String, Object> options = new HashMap<>();
        options.put(TeamcityJob.INSTANCE_URL,"");
        options.put(TeamcityJob.JOB_URL,"");
        options.put(TeamcityJob.JOB_NAME,"");

        Map<String, Object> uniqueOptions = new HashMap<>();
        uniqueOptions.put(TeamcityJob.JOB_URL,"");
        uniqueOptions.put(TeamcityJob.JOB_NAME,"");

        protoType.setAllFields(options);
        protoType.setUniqueFields(uniqueOptions);
        protoType.setSearchFields(Arrays.asList(JOB_NAME,NICE_NAME));
        return protoType;
    }
}
