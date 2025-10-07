package org.dspace.app.raid;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

public class RaidProjectExportConfiguration extends ScriptConfiguration {
    // You can define options here (if arguments are needed), else leave minimal
    @Override
    public String getName() {
        return "raid-project-export";
    }

    @Override
    public Class getDspaceRunnableClass() {
        return null;
    }

    @Override
    public void setDspaceRunnableClass(Class dspaceRunnableClass) {

    }



    @Override
    public Options getOptions() {
        return null;
    }
}
