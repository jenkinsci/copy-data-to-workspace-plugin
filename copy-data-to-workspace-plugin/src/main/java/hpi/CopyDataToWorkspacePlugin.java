/*Copyright (c) 2010, Parallels-NSU lab. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided 
that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions 
    * and the following disclaimer.
    
    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
    * and the following disclaimer in the documentation and/or other materials provided with 
    * the distribution.
    
    * Neither the name of the Parallels-NSU lab nor the names of its contributors may be used to endorse 
    * or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR 
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR 
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package hpi;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class CopyDataToWorkspacePlugin extends BuildWrapper {
	private String folderPath;
	private boolean makeFilesExecutable;
	private boolean deleteFilesAfterBuild;
	private String[] copiedFiles;
	
	private static final Logger log = Logger.getLogger(CopyDataToWorkspacePlugin.class.getName()); 
	
	@DataBoundConstructor
    public CopyDataToWorkspacePlugin(String folderPath, boolean makeFilesExecutable, boolean deleteFilesAfterBuild) {
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
	public Environment setUp(AbstractBuild build, final Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException 
	{
		log.finest("Recognize project workspace and folder");
		FilePath projectWorkspace = build.getWorkspace();
		
		Hudson hudson = Hudson.getInstance();
		FilePath hudsonRoot = hudson.getRootPath();
		
		FilePath copyFrom = new FilePath(hudsonRoot, folderPath);
		
		log.finest("Copying data from " + copyFrom.toURI()
				+ " to " + projectWorkspace.toURI());
		copyFrom.copyRecursiveTo(projectWorkspace);
		
		log.finest("Saving names");
		saveNames(copyFrom);
		
		log.finest("Making executable");
		if (makeFilesExecutable) {
			seeFolder(projectWorkspace);
		}
		
		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener)
					throws IOException, InterruptedException {
				if (deleteFilesAfterBuild) {
					FilePath projectWorkspace = build.getWorkspace();

					for (String str : copiedFiles) {
						log.finest("Deleting file = " + str);
						FilePath child = new FilePath(projectWorkspace, str);
						if (child.isDirectory()) {
							child.deleteRecursive();
						} else {
							child.delete();
						}
					}
				}
				return true;
			}
		};
	}

    @Override
    public Environment setUp(Build build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return setUp(build, launcher, listener);
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(CopyDataToWorkspacePlugin.class);
        }
        
        public FormValidation doCheckFolderPath(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            if (0 == value.length()) {
            	return FormValidation.error("Empty path to folder");
            }
        	return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Copy data to workspace";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

    }
    
    void seeFolder(FilePath path) throws IOException, InterruptedException {
    	List<FilePath> children = path.list();
		for (FilePath child : children) {
			if (child.isDirectory()) {
				seeFolder(child);
			} else {
				child.chmod(0755);
			}
		}
    }
    
    void saveNames(FilePath path) throws IOException, InterruptedException {
    	List<FilePath> children = path.list();
    	this.copiedFiles = new String[children.size()];
    	int i = 0;
		for (FilePath child : children) {
			copiedFiles[i] = child.getName(); 
			i++;
			log.finest("Saving name = " + child.getName());
		}
    }
}