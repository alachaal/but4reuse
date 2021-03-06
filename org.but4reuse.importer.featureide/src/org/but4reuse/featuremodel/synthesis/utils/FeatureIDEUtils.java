package org.but4reuse.featuremodel.synthesis.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.but4reuse.feature.constraints.IConstraint;
import org.but4reuse.utils.files.FileUtils;
import org.but4reuse.utils.workbench.WorkbenchUtils;
import org.eclipse.core.resources.IFile;
import org.prop4j.Node;
import org.prop4j.NodeReader;

import de.ovgu.featureide.fm.core.Constraint;
import de.ovgu.featureide.fm.core.Feature;
import de.ovgu.featureide.fm.core.FeatureModel;
import de.ovgu.featureide.fm.core.io.FeatureModelWriterIFileWrapper;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelWriter;

/**
 * Feature IDE Utils
 * @author jabier.martinez
 */
public class FeatureIDEUtils {

	public static void exportFeatureModel(URI featureModelURI, FeatureModel fm) {
		File fmFile = FileUtils.getFile(featureModelURI);
		try {
			FileUtils.createFile(fmFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		save(fm, fmFile);
		// Refresh in case of workspace
		IFile file = WorkbenchUtils.getIFileFromFile(fmFile);
		if (file != null) {
			WorkbenchUtils.refreshIResource(file);
		}
	}

	/**
	 * 
	 * @param configsFolderURI
	 * @param configurations
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	public static void createConfigurations(URI configsFolderURI, Map<String, List<String>> configurations)
			throws URISyntaxException, IOException {
		for (String key : configurations.keySet()) {
			URI configURI = new URI(configsFolderURI + key + ".config");
			List<String> features = configurations.get(key);
			createConfiguration(configURI, features);
		}
	}

	/**
	 * 
	 * @param configURI
	 * @param feature
	 * @throws IOException
	 */
	public static void createConfiguration(URI configURI, List<String> features) throws IOException {
		File configFile = FileUtils.getFile(configURI);
		FileUtils.createFile(configFile);
		BufferedWriter output = new BufferedWriter(new FileWriter(configFile));
		for (String feature : features) {
			output.append(validFeatureName(feature));
			output.newLine();
		}
		output.close();
	}

	/**
	 * Get string representation of the constraint
	 * @param constraint
	 * @return
	 */
	public static String getConstraintString(IConstraint constraint) {
		String type = constraint.getType();
		// only this two supported for the moment
		if (type.equals(IConstraint.REQUIRES) || type.equals(IConstraint.MUTUALLY_EXCLUDES)) {
			String text = validFeatureName(constraint.getBlock1().getName()) + " implies ";
			if (type.equals(IConstraint.MUTUALLY_EXCLUDES)) {
				text += "not ";
			}
			text += validFeatureName(constraint.getBlock2().getName());
			return text;
		}
		return constraint.getText();
	}

	/**
	 * Save
	 * @param featureModel
	 * @param file
	 */
	public static void save(FeatureModel featureModel, File file) {
		new FeatureModelWriterIFileWrapper(new XmlFeatureModelWriter(featureModel)).writeToFile(file);
	}

	/**
	 * Add constraint
	 * @param featureModel
	 * @param constraint
	 */
	public static void addConstraint(FeatureModel featureModel, String constraint) {
		NodeReader nodeReader = new NodeReader();
		List<String> featureList = new ArrayList<String>(featureModel.getFeatureNames());
		Node node = null;
		try{
			node = nodeReader.stringToNode(constraint, featureList);
		} catch(Exception e){
			System.out.println("Not valid constraint definition: " + constraint);
		}
		// TODO report error if node is null
		if(node!=null){
			Constraint c = new Constraint(featureModel, node);
			featureModel.addConstraint(c);
		} else {
			System.out.println("Maybe problems with names: " + constraint);
		}
	}
	
	/**
	 * Feature IDE has some restrictions like no whitespaces etc.
	 * They say that the feature name must be a valid Java identifier.
	 * @param name
	 * @return
	 */
	public static String validFeatureName(String name){
		// TODO improve checks
		return name.replaceAll(" ", "_");
	}
	

	// For example 3 requires 2 (3 is child of 2). Then 2 requires 1. isAncestor 1 of 3 is true.
	public static boolean isAncestorFeature1ofFeature2(FeatureModel fm, List<IConstraint> constraints, Feature f1, Feature f2) {
		List<Feature> directRequired = getFeatureRequiredFeatures(fm, constraints, f2);
		if (directRequired.contains(f1)) {
			return true;
		}
		for (Feature direct : directRequired) {
			if (isAncestorFeature1ofFeature2(fm, constraints, f1, direct)) {
				return true;
			}
		}
		return false;
	}

	public static boolean existsExcludeConstraint(List<IConstraint> constraints, Feature f1, Feature f2) {
		for (IConstraint constraint : constraints) {
			if (constraint.getType().equals(IConstraint.MUTUALLY_EXCLUDES)) {
				// check f1 excludes f2 and viceversa
				if (f1.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock1().getName()))
						&& f2.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock2().getName()))) {
					return true;
				} else if (f2.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock1().getName()))
						&& f1.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock2().getName()))) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean existsRequiresConstraint(List<IConstraint> constraints, Feature f1, Feature f2) {
		for (IConstraint constraint : constraints) {
			if (constraint.getType().equals(IConstraint.REQUIRES)) {
				if (f1.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock1().getName()))
						&& f2.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock2().getName()))) {
					return true;
				}
			}
		}
		return false;
	}

	public static List<Feature> getFeatureRequiredFeatures(FeatureModel fm, List<IConstraint> constraints, Feature f1) {
		List<Feature> required = new ArrayList<Feature>();
		for (IConstraint constraint : constraints) {
			if (constraint.getType().equals(IConstraint.REQUIRES)) {
				if (f1.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock1().getName()))) {
					required.add(fm.getFeature(FeatureIDEUtils.validFeatureName(constraint.getBlock2().getName())));
				}
			}
		}
		return required;
	}
	
	public static int getNumberOfReasonsOfRequiresConstraint(List<IConstraint> constraints, Feature f1, Feature f2) {
		for (IConstraint constraint : constraints) {
			if (constraint.getType().equals(IConstraint.REQUIRES)) {
				if (f1.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock1().getName()))
						&& f2.getName().equals(FeatureIDEUtils.validFeatureName(constraint.getBlock2().getName()))) {
					return constraint.getNumberOfReasons();
				}
			}
		}
		return -1;
	}

}
