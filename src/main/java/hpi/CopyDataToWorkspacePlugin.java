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

// Hudson/Jenkins core imports
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import static hudson.Functions.isWindows;
import jenkins.model.Jenkins;

// Java standard imports
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

// JSON/Stapler imports
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

public class CopyDataToWorkspacePlugin extends BuildWrapper {
	private String folderPath;
	private boolean makeFilesExecutable;
	private boolean deleteFilesAfterBuild;
	private String[] copiedFiles = new String[0];
	
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
		
        Jenkins jenkins = Jenkins.get();
        FilePath jenkinsRoot = jenkins.getRootPath();
		
		// Restrict access to userContent directory only
		FilePath userContentDir = new FilePath(jenkinsRoot, "userContent");
		FilePath copyFrom = new FilePath(userContentDir, folderPath);
		
		// Verify if the final path is within allowed directory
		if (!copyFrom.getRemote().startsWith(userContentDir.getRemote())) {
		    throw new IOException("The source path must be within the JENKINS_HOME/userContent directory");
		}
		
		if (!copyFrom.exists()) {
		    throw new IOException("The specified source path does not exist: " + copyFrom.getRemote());
		}
		log.finest("Copying data from " + copyFrom.toURI() + " to " + projectWorkspace.toURI());
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
    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(CopyDataToWorkspacePlugin.class);
        }
        
        public FormValidation doCheckFolderPath(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            if (project != null) {
                project.checkPermission(Item.CONFIGURE);
            }
            return validateFolderPath(value);
        }

		public static FormValidation validateFolderPath(String value) {
			if (value == null || value.trim().isEmpty()) {
				return FormValidation.error("Path cannot be empty");
			}

			String normalized = value.replace('\\', '/').trim();
			String[] parts = normalized.split("/");

			for (String part : parts) {
				if (part.equals("..")) {
					return FormValidation.error("Path traversal .. is not allowed");
				}
			}

			if (normalized.startsWith("~/") || normalized.equals("~")) {
				return FormValidation.error("Leading ~ is not allowed");
			}

			if (isWindows()) {
				if (normalized.matches("^[A-Za-z]:.*") || normalized.startsWith("//")) {
					return FormValidation.error("Absolute paths are not allowed");
				}
				if (normalized.matches(".*[<>:\"|?*].*")) {
					return FormValidation.error("Invalid Windows characters: <, >, :, \", |, ?, *");
				}
			} else {
				if (normalized.startsWith("/")) {
					return FormValidation.error("Absolute paths are not allowed");
				}
				if (normalized.chars().anyMatch(Character::isISOControl)) {
					return FormValidation.error("Control characters are not allowed");
				}
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

        @Override
        public BuildWrapper newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            String folderPath = formData.getString("folderPath");
            FormValidation validation = validateFolderPath(folderPath);
            if (validation.kind == FormValidation.Kind.ERROR) {
                throw new FormException(validation.getMessage(), "folderPath");
            }
            return super.newInstance(req, formData);
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