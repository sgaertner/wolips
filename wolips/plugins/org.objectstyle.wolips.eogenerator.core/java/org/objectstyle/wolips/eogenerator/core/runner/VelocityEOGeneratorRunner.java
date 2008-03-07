package org.objectstyle.wolips.eogenerator.core.runner;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogSystem;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import org.apache.velocity.tools.generic.ListTool;
import org.eclipse.core.runtime.IPath;
import org.objectstyle.wolips.eogenerator.core.Activator;
import org.objectstyle.wolips.eogenerator.core.model.EOGeneratorModel;
import org.objectstyle.wolips.eogenerator.core.model.EOGeneratorResourceLoader;
import org.objectstyle.wolips.eogenerator.core.model.EOModelReference;
import org.objectstyle.wolips.eogenerator.core.model.IEOGeneratorRunner;
import org.objectstyle.wolips.eogenerator.core.model.EOGeneratorModel.Define;
import org.objectstyle.wolips.eomodeler.core.model.EOEntity;
import org.objectstyle.wolips.eomodeler.core.model.EOModel;
import org.objectstyle.wolips.eomodeler.core.model.EOModelGroup;
import org.objectstyle.wolips.eomodeler.core.model.EOModelRenderContext;
import org.objectstyle.wolips.eomodeler.core.model.EOModelVerificationFailure;
import org.objectstyle.wolips.thirdparty.velocity.resourceloader.ResourceLoader;

public class VelocityEOGeneratorRunner implements IEOGeneratorRunner {
	public static class ConsoleLogger implements LogSystem {
		public void init(RuntimeServices runtimeservices) throws Exception {
			// DO NOTHING
		}

		public void logVelocityMessage(int i, String s) {
			System.out.println("ConsoleLogger.logVelocityMessage: " + i + ", " + s);
		}
	}

	private boolean _insideEclipse;
	
	public VelocityEOGeneratorRunner() {
		this(true);
	}
	
	public VelocityEOGeneratorRunner(boolean insideEclipse) {
		_insideEclipse = insideEclipse;
	}
	
	public boolean generate(EOGeneratorModel eogeneratorModel, StringBuffer results) throws Exception {
		return generate(eogeneratorModel, results, null);
	}
	
	public boolean generate(EOGeneratorModel eogeneratorModel, StringBuffer results, EOModelGroup preloadedModelGroup) throws Exception {
		boolean showResults = false;
		VelocityEngine velocityEngine = new VelocityEngine();
		velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, org.apache.velocity.runtime.log.NullLogSystem.class.getName());

		StringBuffer templatePaths = new StringBuffer();
		templatePaths.append(".");
		String templatePath = eogeneratorModel.getTemplateDir();
		if (templatePath != null && templatePath.length() > 0) {
			templatePaths.append(",");
			File templateFolder = new File(templatePath);
			if (!templateFolder.isAbsolute()) {
				templateFolder = new File(eogeneratorModel.getProjectPath().toFile(), templatePath);
			}
			templatePaths.append(templateFolder.getAbsolutePath());
		}

		String superclassTemplateName = eogeneratorModel.getJavaTemplate();
		String subclassTemplateName = eogeneratorModel.getSubclassJavaTemplate();

		boolean eogeneratorJava14 = eogeneratorModel.isJava14();
		if (eogeneratorJava14) {
			if (superclassTemplateName == null || superclassTemplateName.length() == 0) {
				superclassTemplateName = "_Entity14.java";
			}
			if (subclassTemplateName == null || subclassTemplateName.length() == 0) {
				subclassTemplateName = "Entity14.java";
			}
		} else {
			if (superclassTemplateName == null || superclassTemplateName.length() == 0) {
				superclassTemplateName = "_Entity.java";
			}
			if (subclassTemplateName == null || subclassTemplateName.length() == 0) {
				subclassTemplateName = "Entity.java";
			}
		}

		templatePaths.append(",");
		templatePaths.append(new File("/Library/Application Support/WOLips/EOGenerator").getAbsolutePath());
		templatePaths.append(",");
		templatePaths.append(new File(System.getProperty("user.home"), "Documents and Settings/Application Data/WOLips/EOGenerator").getAbsolutePath());
		templatePaths.append(",");
		templatePaths.append(new File(System.getProperty("user.home"), "Documents and Settings/AppData/Local/WOLips/EOGenerator").getAbsolutePath());
		templatePaths.append(",");
		templatePaths.append(new File(System.getProperty("user.home"), "Library/Application Support/WOLips/EOGenerator").getAbsolutePath());

		velocityEngine.setProperty("resource.loader", "file,wolips");
		velocityEngine.setProperty("file.resource.loader.class", FileResourceLoader.class.getName());
		velocityEngine.setProperty("file.resource.loader.path", templatePaths.toString());
		if (_insideEclipse) {
			velocityEngine.setProperty("wolips.resource.loader.class", ResourceLoader.class.getName());
			velocityEngine.setProperty("wolips.resource.loader.bundle", Activator.getDefault().getBundle());
		}
		else {
			velocityEngine.setProperty("wolips.resource.loader.class", EOGeneratorResourceLoader.class.getName());
		}

		velocityEngine.init();
		VelocityContext context = new VelocityContext();

		List<EOModel> models = new LinkedList<EOModel>();
		EOModelRenderContext renderContext = new EOModelRenderContext();
		try {
			String prefix = eogeneratorModel.getPrefix();
			if (prefix != null) {
				renderContext.setPrefix(prefix);
			}
			renderContext.setSuperclassPackage(eogeneratorModel.getSuperclassPackage());
			String eogenericRecordClassName = eogeneratorModel.getDefineValueNamed("EOGenericRecord");
			if (eogenericRecordClassName != null) {
				renderContext.setEOGenericRecordClassName(eogenericRecordClassName);
			}
			renderContext.setJavaClient(eogeneratorModel.isJavaClient() != null && eogeneratorModel.isJavaClient().booleanValue());
			EOModelRenderContext.setRenderContext(renderContext);

			EOModelGroup modelGroup;
			if (preloadedModelGroup == null) {
				modelGroup = new EOModelGroup();
				for (EOModelReference modelRef : eogeneratorModel.getModels()) {
					String modelPath = modelRef.getPath((IPath) null);
					File modelFile = new File(modelPath);
					if (!modelFile.isAbsolute()) {
						modelFile = new File(eogeneratorModel.getProjectPath().toFile(), modelPath);
					}
					EOModel model = modelGroup.loadModelFromURL(modelFile.toURL());
					models.add(model);
				}
				for (EOModelReference modelRef : eogeneratorModel.getRefModels()) {
					String modelPath = modelRef.getPath((IPath) null);
					File modelFile = new File(modelPath);
					if (!modelFile.isAbsolute()) {
						modelFile = new File(eogeneratorModel.getProjectPath().toFile(), modelPath);
					}
					modelGroup.loadModelFromURL(modelFile.toURL());
				}
				Set<EOModelVerificationFailure> failures = new HashSet<EOModelVerificationFailure>();
				modelGroup.resolve(failures);
				modelGroup.verify(failures);

				for (EOModelVerificationFailure failure : failures) {
					if (!failure.isWarning()) {
						results.append("Error: " + failure.getMessage() + "\n");
						showResults = true;
					}
				}
			}
			else {
				modelGroup = preloadedModelGroup;
				for (EOModelReference modelRef : eogeneratorModel.getModels()) {
					String modelName = modelRef.getName();
					EOModel model = modelGroup.getModelNamed(modelName);
					if (model == null) {
						throw new RuntimeException("There was no model named '" + modelName + "' in this model group.");
					}
					models.add(model);
				}
			}

			File superclassDestination = new File(eogeneratorModel.getDestination());
			if (!superclassDestination.isAbsolute()) {
				superclassDestination = new File(eogeneratorModel.getProjectPath().toFile(), eogeneratorModel.getDestination());
			}
			if (!superclassDestination.exists()) {
				if (!superclassDestination.mkdirs()) {
					throw new IOException("Failed to create destination '" + superclassDestination + "'.");
				}
			}

			File subclassDestination = new File(eogeneratorModel.getSubclassDestination());
			if (!subclassDestination.isAbsolute()) {
				subclassDestination = new File(eogeneratorModel.getProjectPath().toFile(), eogeneratorModel.getSubclassDestination());
			}
			if (!subclassDestination.exists()) {
				if (!subclassDestination.mkdirs()) {
					throw new IOException("Failed to create subclass destination '" + subclassDestination + "'.");
				}
			}

			// String filePathTemplate = eogeneratorModel.getFilenameTemplate();
			// if (filePathTemplate == null || filePathTemplate.trim().length()
			// == 0) {
			// }

			context.put("eogeneratorModel", eogeneratorModel);
			for (Define define : eogeneratorModel.getDefines()) {
				context.put(define.getName(), define.getValue());
			}
			context.put("list", new ListTool());
			for (EOModel model : models) {
				// System.out.println("Generating " + model.getName() + " ...");
				context.put("model", model);

				for (EOEntity entity : model.getEntities()) {
					// System.out.println("Generating " + model.getName() + "."
					// + entity.getName() + " ...");
					context.put("entity", entity);

					String classNameWithPackage = renderContext.getClassNameForEntity(entity);
					boolean eogenericRecord = classNameWithPackage == null || classNameWithPackage.endsWith("GenericRecord");
					if (entity.isGenerateSource() && !eogenericRecord) {
						String prefixClassNameWithPackage = entity.getPrefixClassName();
						context.put("className", classNameWithPackage);
						context.put("prefixClassName", prefixClassNameWithPackage);
						context.put("packageName", entity.getPackageName());
						context.put("classNameWithoutPackage", entity.getClassNameWithoutPackage());
						context.put("prefixClassNameWithoutPackage", entity.getPrefixClassNameWithoutPackage());

						String superclassFileTemplate = prefixClassNameWithPackage;
						// StringWriter superclassFilePathWriter = new
						// StringWriter();
						// velocityEngine.evaluate(context,
						// superclassFilePathWriter, "LOG",
						// superclassFileTemplate);

						String superclassFilePath = superclassFileTemplate.toString().replace('.', '/') + ".java";
						File superclassFile = new File(superclassDestination, superclassFilePath);
						File superclassFolder = superclassFile.getParentFile();
						if (!superclassFolder.exists()) {
							if (!superclassFolder.mkdirs()) {
								throw new IOException("Unable to make superclass folder '" + superclassFolder + "'.");
							}
						}
						VelocityEOGeneratorRunner.writeTemplate(velocityEngine, context, superclassTemplateName, superclassFile);

						String subclassFileTemplate = classNameWithPackage;
						// StringWriter subclassFilePathWriter = new
						// StringWriter();
						// velocityEngine.evaluate(context,
						// subclassFilePathWriter, "LOG", subclassFileTemplate);

						String subclassFilePath = subclassFileTemplate.toString().replace('.', '/') + ".java";
						File subclassFile = new File(subclassDestination, subclassFilePath);
						File subclassFolder = subclassFile.getParentFile();
						if (!subclassFolder.exists()) {
							if (!subclassFolder.mkdirs()) {
								throw new IOException("Unable to make subclass folder '" + superclassFolder + "'.");
							}
						}
						if (!subclassFile.exists()) {
							VelocityEOGeneratorRunner.writeTemplate(velocityEngine, context, subclassTemplateName, subclassFile);
						}
					}
				}
			}
		} finally {
			EOModelRenderContext.clearRenderContext();
		}
		return showResults;
	}

	public static void writeTemplate(VelocityEngine engine, VelocityContext context, String templateName, File outputFile) throws ResourceNotFoundException, ParseErrorException, MethodInvocationException, Exception {
		Template template;
		try {
			template = engine.getTemplate(templateName);
		} catch (ResourceNotFoundException e) {
			throw new Exception("Failed to load the template '" + templateName + "'.  Check your model's eogen file to make sure that it specifies the correct template folder and template names.");
		}
		if (!outputFile.getParentFile().exists()) {
			if (!outputFile.getParentFile().mkdirs()) {
				throw new IOException("Unable to create the folder " + outputFile.getParentFile() + ".");
			}
		}

		ByteArrayOutputStream newFileContentsStream = new ByteArrayOutputStream();
		Writer newFileContentsWriter = new OutputStreamWriter(newFileContentsStream);
		try {
			template.merge(context, newFileContentsWriter);
		} finally {
			newFileContentsWriter.close();
		}
		String newFileContentsStr = newFileContentsStream.toString();
		if (newFileContentsStr != null) {
			if (newFileContentsStr.contains("<%")) {
				throw new IOException("You are attempting to use an old EOGenerator template with Velocity EOGenerator.");
			} else if (newFileContentsStr.contains("<wo:")) {
				throw new IOException("You are attempting to use a JavaEOGenerator template with Velocity EOGenerator.");
			}
		}

		boolean templateChanged = true;
		if (!outputFile.exists()) {
			FileWriter newFileWriter = new FileWriter(outputFile);
			BufferedWriter newFileBufferedWriter = new BufferedWriter(newFileWriter);
			try {
				newFileBufferedWriter.write(newFileContentsStr);
			} finally {
				newFileBufferedWriter.close();
			}
		} else {
			byte[] newFileContents = newFileContentsStream.toByteArray();
			if (outputFile.exists()) {
				FileInputStream fis = new FileInputStream(outputFile);
				int bytesRemaining = (int) outputFile.length();
				if (bytesRemaining == newFileContents.length) {
					byte[] oldFileContents;
					try {
						ByteArrayOutputStream bos = new ByteArrayOutputStream((int) outputFile.length());
						byte[] buf = new byte[4096];
						while (bytesRemaining > 0) {
							int bytesRead = fis.read(buf, 0, Math.min(buf.length, bytesRemaining));
							bos.write(buf, 0, bytesRead);
							bytesRemaining -= bytesRead;
						}
						oldFileContents = bos.toByteArray();
					} finally {
						fis.close();
					}

					MessageDigest md5 = MessageDigest.getInstance("MD5");
					byte[] oldMD5 = md5.digest(oldFileContents);
					md5.reset();
					byte[] newMD5 = md5.digest(newFileContents);
					md5.reset();

					if (oldMD5.length == newMD5.length) {
						templateChanged = false;
						for (int i = 0; !templateChanged && i < oldMD5.length; i++) {
							if (oldMD5[i] != newMD5[i]) {
								templateChanged = true;
							}
						}
					}
				}
			}

			if (templateChanged) {
				FileOutputStream newFileStream = new FileOutputStream(outputFile);
				BufferedOutputStream newFileBufferedStream = new BufferedOutputStream(newFileStream);
				try {
					newFileBufferedStream.write(newFileContents);
				} finally {
					newFileBufferedStream.close();
				}
			} else {
				// System.out.println("EOGenerator.writeTemplate: skipping " +
				// outputFile);
			}
		}
	}
}
