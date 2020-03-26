package com.capitalone.dashboard.model;

/**
 * CollectorItem extension to store the instance, build job and build url.
 */
public class TeamcityProject extends JobCollectorItem {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
        	return true;
        }
        if (o == null || getClass() != o.getClass()) {
        	return false;
        }

        TeamcityProject hudsonJob = (TeamcityProject) o;

        return getJobUrl().equals(hudsonJob.getJobUrl()) && getJobName().equals(hudsonJob.getJobName());
    }

    @Override
    public int hashCode() {
        int result = getJobUrl().hashCode();
        result = 31 * result + getJobName().hashCode();
        return result;
    }
}
