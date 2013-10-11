package org.flexiblepower.miele.protocol;

import java.util.Date;

public class MieleAppliance {

    private final String applianceType;
    private final String applianceId;

    private Date lastActionTime;

    public MieleAppliance(String applianceType, String applianceId) {
        super();
        this.applianceType = applianceType;
        this.applianceId = applianceId;

        lastActionTime = new Date(0); // init to very long ago (the epoch)
    }

    public String getApplianceType() {
        return applianceType;
    }

    public String getApplianceId() {
        return applianceId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((applianceId == null) ? 0 : applianceId.hashCode());
        result = prime * result + ((applianceType == null) ? 0 : applianceType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MieleAppliance other = (MieleAppliance) obj;
        if (applianceId == null) {
            if (other.applianceId != null) {
                return false;
            }
        } else if (!applianceId.equals(other.applianceId)) {
            return false;
        }
        if (applianceType == null) {
            if (other.applianceType != null) {
                return false;
            }
        } else if (!applianceType.equals(other.applianceType)) {
            return false;
        }
        return true;
    }

    public Date getLastActionTime() {
        return lastActionTime;
    }

    protected void setLastActionTime(Date actionTime) {
        lastActionTime = actionTime;
    }

}
