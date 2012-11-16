/*
 * Copyright (c) 2010, Parallels-NSU lab. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the following conditions
 * are met: Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. Neither the name of the Parallels-NSU lab
 * nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package hpi;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class CopyDataToWorkspacePlugin extends BuildWrapper {

    private final String folderPath;
    private final boolean makeFilesExecutable;
    private final boolean deleteFilesAfterBuild;
    private final static String delimiter = ",";
    private List<String> copiedFilenames;

    private static final Logger log = Logger.getLogger(CopyDataToWorkspacePlugin.class.getName());

    @DataBoundConstructor
    public CopyDataToWorkspacePlugin(final String folderPath, final boolean makeFilesExecutable, final boolean deleteFilesAfterBuild) {
        this.folderPath = folderPath;
        this.makeFilesExecutable = makeFilesExecutable;
        this.deleteFilesAfterBuild = deleteFilesAfterBuild;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public boolean getMakeFilesExecutable() {
        return makeFilesExecutable;
    }

    public boolean getDeleteFilesAfterBuild() {
        return deleteFilesAfterBuild;
    }

    @Override
    public Environment setUp(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException,
            InterruptedException {
        log.finest("Recognize project workspace and folder");
        final FilePath projectWorkspace = build.getWorkspace();

        final Hudson hudson = Hudson.getInstance();
        final FilePath hudsonRoot = hudson.getRootPath();

        log.finest("Given folders: " + folderPath);
        final String[] currentFolderPath = folderPath.split(delimiter);
        for (int i = 0; i < currentFolderPath.length; i++) {
            final String trimmedFolderPath = trimLeftAndRight(currentFolderPath[i]);

            final FilePath copyFrom = new FilePath(hudsonRoot, trimmedFolderPath);

            log.finest("Copying data from '" + copyFrom.toURI() + "' to '" + projectWorkspace.toURI() + "'");
            copyFrom.copyRecursiveTo(projectWorkspace);

            saveNames(copyFrom);

            if (makeFilesExecutable) {
                log.finest("Making executable");
                seeFolder(projectWorkspace);
            }
        }
        return new Environment() {
            @Override
            public boolean tearDown(final AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                if (deleteFilesAfterBuild) {
                    final FilePath projectWorkspace = build.getWorkspace();

                    for (final String currentFilename : copiedFilenames) {
                        final FilePath child = new FilePath(projectWorkspace, currentFilename);
                        if (child.isDirectory()) {
                            log.finest("Deleting directory '" + currentFilename + "'");
                            child.deleteRecursive();
                        } else {
                            log.finest("Deleting file '" + currentFilename + "'");
                            child.delete();
                        }
                    }
                }
                return true;
            }
        };
    }

    @Override
    public Environment setUp(final Build build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        return setUp(build, launcher, listener);
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(CopyDataToWorkspacePlugin.class);
        }

        public FormValidation doCheckFolderPath(@AncestorInPath final AbstractProject project, @QueryParameter final String value)
                throws IOException {
            if (0 == value.length()) {
                return FormValidation.error("Empty path to folder");
            } else {
                final Hudson hudson = Hudson.getInstance();
                final FilePath hudsonRoot = hudson.getRootPath();
                final String[] currentFolderPath = value.split(delimiter);
                for (int i = 0; i < currentFolderPath.length; i++) {
                    final String trimmedFolderPath = trimLeftAndRight(currentFolderPath[i]);
                    final FilePath copyFrom = new FilePath(hudsonRoot, trimmedFolderPath);
                    try {
                        if (!copyFrom.exists()) {
                            return FormValidation.error("Folder '" + trimmedFolderPath + "' not found");
                        }
                    } catch (final InterruptedException e) {
                        log.finest("ERROR: " + e.getMessage());
                        return FormValidation.error("Unable to evaluate given folder");
                    }
                }
            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Copy data to workspace";
        }

        @Override
        public boolean isApplicable(final AbstractProject<?, ?> item) {
            return true;
        }

    }

    void seeFolder(final FilePath path) throws IOException, InterruptedException {
        final List<FilePath> children = path.list();
        for (final FilePath child : children) {
            if (child.isDirectory()) {
                seeFolder(child);
            } else {
                child.chmod(0755);
            }
        }
    }

    void saveNames(final FilePath path) throws IOException, InterruptedException {
        if (copiedFilenames == null) {
            copiedFilenames = new ArrayList<String>();
        }
        final List<FilePath> children = path.list();
        for (final FilePath child : children) {
            log.finest("Storing file/folder name '" + child.getName() + "'");
            copiedFilenames.add(child.getName());
        }
    }

    /**
     * Remove leading and trailing whitespaces on given string. This might be the case if something
     * like "jobs/foo/workspace/, jobs/bar/workspace/" is configured, where the "," is followed by a
     * whitespace.
     * 
     * @param givenString
     *        - String with possible leading and trailing whitespaces
     * @return String without whitespace on the begin a/o end
     */
    protected static String trimLeftAndRight(final String givenString) {
        return givenString.replaceAll("^\\s+", "").replaceAll("\\s+$", "");
    }
}
