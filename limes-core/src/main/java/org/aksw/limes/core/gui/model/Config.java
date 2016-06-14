package org.aksw.limes.core.gui.model;

import static org.aksw.limes.core.gui.util.SourceOrTarget.SOURCE;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;

import org.aksw.limes.core.execution.engine.SimpleExecutionEngine;
import org.aksw.limes.core.execution.planning.plan.NestedPlan;
import org.aksw.limes.core.execution.planning.planner.HeliosPlanner;
import org.aksw.limes.core.gui.model.metric.MetricFormatException;
import org.aksw.limes.core.gui.model.metric.MetricParser;
import org.aksw.limes.core.gui.model.metric.Output;
import org.aksw.limes.core.gui.util.SourceOrTarget;
import org.aksw.limes.core.gui.util.sparql.PrefixHelper;
import org.aksw.limes.core.io.cache.Cache;
import org.aksw.limes.core.io.config.Configuration;
import org.aksw.limes.core.io.config.KBInfo;
import org.aksw.limes.core.io.config.reader.AConfigurationReader;
import org.aksw.limes.core.io.config.reader.rdf.RDFConfigurationReader;
import org.aksw.limes.core.io.config.reader.xml.XMLConfigurationReader;
import org.aksw.limes.core.io.config.writer.RDFConfigurationWriter;
import org.aksw.limes.core.io.config.writer.XMLConfigurationWriter;
import org.aksw.limes.core.io.ls.LinkSpecification;
import org.aksw.limes.core.io.mapping.AMapping;
import org.aksw.limes.core.ml.algorithm.eagle.util.PropertyMapping;

import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ListView;

/**
 * Contains all important information of the LIMES-Query
 *
 * @author Manuel Jacob, Sascha Hahne, Daniel Obraczka, Felix Brei
 */
public class Config extends Configuration {

    /**
     * PorpertyMapping of current query
     */
    public PropertyMapping propertyMapping;
    /**
     * current Metric
     */
    private Output metric = null;
    /**
     * Source Endpoint
     */
    private Endpoint sourceEndpoint;
    /**
     * Target Endpoint
     */
    private Endpoint targetEndpoint;
    /**
     * Mapping of current MappingTask
     */
    private AMapping mapping;

    /**
     * Constructor
     */
    public Config() {
	this.sourceInfo = new KBInfo();
	this.sourceInfo.setVar("?source");
	this.targetInfo = new KBInfo();
	this.targetInfo.setVar("?target");
	metric = new Output();
	this.sourceEndpoint = new Endpoint(this.sourceInfo);
	this.targetEndpoint = new Endpoint(this.targetInfo);
    }

    public Config(KBInfo sourceInfo, KBInfo targetInfo, String metricExpression, String acceptanceRelation, String verificationRelation,
	    double acceptanceThreshold, String acceptanceFile, double verificationThreshold, String verificationFile, int exemplars,
	    HashMap<String, String> prefixes, String outputFormat, String executionPlanner, int granularity, String recallRegulator, double recallThreshold) {
	super(sourceInfo, targetInfo, metricExpression, acceptanceRelation, verificationRelation, acceptanceThreshold, acceptanceFile, verificationThreshold,
		verificationFile, prefixes, outputFormat, null, executionPlanner, null, granularity, null, null, null, null, null);
	metric = new Output();
	this.sourceEndpoint = new Endpoint(this.sourceInfo);
	this.targetEndpoint = new Endpoint(this.targetInfo);
    }

    /**
     * loads the linkspec from file
     *
     * @param file
     *            file that should be loaded
     * @return Config Config with loaded file
     * @throws Exception
     *             FileNotFoundException
     */
    public static Config loadFromFile(File file) throws Exception {
	AConfigurationReader reader = null;
	if (file.getAbsolutePath().contains(".xml")) {
	    reader = new XMLConfigurationReader(file.getPath());
	} else if (file.getAbsolutePath().contains(".rdf") || file.getAbsolutePath().contains(".ttl") || file.getAbsolutePath().contains(".n3")
		|| file.getAbsolutePath().contains(".nt")) {
	    reader = new RDFConfigurationReader(file.getPath());
	} else {
	    Alert alert = new Alert(AlertType.ERROR);
	    alert.setHeaderText("An Error occurred!");
	    alert.setContentText("Unknown filetype!");
	    alert.showAndWait();
	}
	Config outConfig;
	Configuration tmp = reader.read();
	outConfig = new Config(tmp.getSourceInfo(), tmp.getTargetInfo(), tmp.getMetricExpression(), tmp.getAcceptanceRelation(), tmp.getVerificationRelation(),
		tmp.getAcceptanceThreshold(), tmp.getAcceptanceFile(), tmp.getVerificationThreshold(), tmp.getVerificationFile(), tmp.getGranularity(),
		(HashMap<String, String>) tmp.getPrefixes(), tmp.getOutputFormat(), tmp.getExecutionPlanner(), tmp.getGranularity(), tmp.getAcceptanceFile(),
		tmp.getAcceptanceThreshold());
	outConfig.sourceEndpoint = new Endpoint(outConfig.getSourceInfo());
	outConfig.targetEndpoint = new Endpoint(outConfig.getTargetInfo());
	outConfig.metric = MetricParser.parse(outConfig.metricExpression, outConfig.getSourceInfo().getVar().replaceAll("\\?", ""));
	outConfig.metric.param1 = outConfig.acceptanceThreshold;
	outConfig.metric.param2 = outConfig.verificationThreshold;
	return outConfig;
    }

    /**
     * saves the config to file
     *
     * @param file
     *            file in which the config should be saved
     * @throws Exception
     *             FileNotFoundException
     */
    public void save(File file) throws Exception {
	if (!metric.isComplete()) {
	    throw new MetricFormatException();
	}
	String format = file.getName().substring(file.getName().lastIndexOf("."), file.getName().length());
	if (format.equals(".xml")) {
	    XMLConfigurationWriter xmlwriter = new XMLConfigurationWriter();
	    xmlwriter.write(this, file.getAbsolutePath());
	} else {
	    RDFConfigurationWriter rdfwriter = new RDFConfigurationWriter();
	    rdfwriter.write(this, file.getAbsolutePath());
	}
    }

    /**
     * Returns the Acceptance Threshold
     *
     * @return the Acceptance Threshold
     */
    public double getAcceptanceThreshold() {
	if (metric == null || metric.param1 == null) {
	    return 1d;
	}
	return metric.param1;
    }

    /**
     * Sets the acceptanceThreshold
     *
     * @param acceptanceThreshold
     */
    public void setAcceptanceThreshold(double acceptanceThreshold) {
	if (metric == null)
	    metric = new Output();
	metric.param1 = acceptanceThreshold;
    }

    /**
     * Returns the Verification Threshold
     *
     * @return Verification Threshold
     */
    public double getVerificationThreshold() {
	if (metric == null || metric.param2 == null) {
	    DecimalFormat twoDForm = new DecimalFormat("#.####");
	    System.out.println("guessed verfication threshold: " + (getAcceptanceThreshold() - 0.1d));
	    NumberFormat format = NumberFormat.getInstance();
	    Number number;
	    try {
		number = format.parse(twoDForm.format(getAcceptanceThreshold() - 0.1d));
	    } catch (Exception e) {
		System.err.println(e);
		return 0.8d;
	    }
	    return number.doubleValue();
	} else
	    return metric.param2;
    }

    /**
     * creates the Task in which the mappingProcess is realized
     *
     * @param results
     *            of the mapping
     * @return null
     */
    public Task<Void> createMappingTask(ObservableList<Result> results) {
	return new Task<Void>() {
	    @Override
	    protected Void call() {
		Cache sourceCache = sourceEndpoint.getCache();
		Cache targetCache = targetEndpoint.getCache();
		LinkSpecification ls = new LinkSpecification();
		ls.readSpec(getMetricExpression(), getAcceptanceThreshold());
		HeliosPlanner hp = new HeliosPlanner(sourceCache, targetCache);
		NestedPlan plan = hp.plan(ls);
		SimpleExecutionEngine ee = new SimpleExecutionEngine(sourceCache, targetCache, getSourceInfo().getVar(), getTargetInfo().getVar());
		mapping = ee.executeStatic(plan);
		mapping.getMap().forEach((sourceURI, map2) -> {
		    map2.forEach((targetURI, value) -> {
			results.add(new Result(sourceURI, targetURI, value));
		    });
		});
		return null;
	    }
	};

    }

    /**
     * Getter SourceEndpoint
     *
     * @return SourceEndpoint
     */
    public Endpoint getSourceEndpoint() {
	return sourceEndpoint;
    }

    /**
     * Getter TargetEndpoint
     *
     * @return TargetEndpoint
     */
    public Endpoint getTargetEndpoint() {
	return targetEndpoint;
    }

    /**
     * Sets the metric to the metricExpression and source using the MetricParser
     *
     * @param metricExpression
     *            to be written to metric
     */
    public void setMetricExpression(String metricExpression) {
	this.metricExpression = metricExpression;
	if (metric != null) {
	    double param1 = 2.0d;
	    double param2 = 2.0d;
	    if (metric.param1 != null)
		param1 = metric.param1;
	    if (metric.param2 != null)
		param2 = metric.param2;
	    metric = MetricParser.parse(metricExpression, getSourceInfo().getVar().replaceAll("\\?", ""));
	    if (param1 <= 1)
		metric.param1 = param1;
	    if (param2 <= 1)
		metric.param2 = param2;
	} else {
	    metric = MetricParser.parse(metricExpression, getSourceInfo().getVar().replaceAll("\\?", ""));
	}
    }

    /**
     * Returns the property Label
     *
     * @param index
     *            Index of Porperty
     * @param sourceOrTarget
     *            is Source or Target
     * @return Property String
     */
    public String getPropertyString(int index, SourceOrTarget sourceOrTarget) {
	if (sourceOrTarget == SOURCE) {
	    return getSourceInfo().getVar().substring(1) + "." + getSourceInfo().getProperties().get(index);

	} else {
	    return getTargetInfo().getVar().substring(1) + "." + getTargetInfo().getProperties().get(index);
	}
    }

    /**
     * Getter Metric as Output
     *
     * @return Metric
     */
    public Output getMetric() {
	return this.metric;
    }

    public HashMap<String, String> getPrefixes() {
	return (HashMap<String, String>) this.prefixes;
    }

    /**
     * Setter PropertyMatching
     *
     * @param propertyPairs
     *            Pairs of Properties
     */
    public void setPropertiesMatching(ListView<String> sourcePropertiesToAdd, ListView<String> targetPropertiesToAdd) {
	List<String> sourceProperties = sourceEndpoint.getInfo().getProperties();
	List<String> targetProperties = targetEndpoint.getInfo().getProperties();
	sourceProperties.clear();
	targetProperties.clear();
	for (String sourceProp : sourcePropertiesToAdd.getItems()) {
	    sourceProperties.add(sourceProp);
	    addFunction(sourceEndpoint, sourceProp);
	}
	for (String targetProp : targetPropertiesToAdd.getItems()) {
	    targetProperties.add(targetProp);
	    addFunction(targetEndpoint, targetProp);
	}
    }

    private void addFunction(Endpoint endpoint, String property) {
	KBInfo info = endpoint.getInfo();
	String abbr = PrefixHelper.abbreviate(property);
	HashMap<String, String> map = new HashMap<String, String>();
	map.put(abbr, "nolang->lowercase");
	info.getFunctions().put(abbr, map);

	String[] parts = property.split(":");
	String prefixToAdd = parts[0];
	info.getPrefixes().put(prefixToAdd, PrefixHelper.getURI(prefixToAdd));
    }

    public AMapping getMapping() {
	return mapping;
    }

    public void setMapping(AMapping mapping) {
	this.mapping = mapping;
    }

}
