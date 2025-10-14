package org.dspace.app.raid;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.dspace.scripts.DSpaceRunnable;

/**
 * Configuration for the RAID export script.
 */
public class RaidProjectExportConfiguration
        extends ScriptConfiguration<RaidProjectExport> {

    @Override
    public Class<RaidProjectExport> getDspaceRunnableClass() {
        return RaidProjectExport.class;
    }

    @Override
    public void setDspaceRunnableClass(Class<RaidProjectExport> dspaceRunnableClass) {
        // This setter is required, but you can ignore or store it if needed
    }

    @Override
    public Options getOptions() {
        // Return CLI options your script uses; return empty or null if none
        return new Options();
    }
}