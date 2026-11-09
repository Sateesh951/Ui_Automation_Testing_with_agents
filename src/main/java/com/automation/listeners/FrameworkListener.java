package com.automation.listeners;

import com.automation.config.FrameworkConfig;
import com.automation.reporting.ReportManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class FrameworkListener implements ISuiteListener, ITestListener {
    @Override
    public void onStart(ISuite suite) {
        ReportManager.start(FrameworkConfig.get("reports.directory", "reports"));
        Configurator.reconfigure();
        Logger logger = LogManager.getLogger(FrameworkListener.class);
        logger.info("Starting suite '{}' with reports at {}", suite.getName(), ReportManager.reportDirectory());
    }

    @Override
    public void onFinish(ISuite suite) {
        ReportManager.linkCucumberScreenshots();
        ReportManager.finishReport();
        LogManager.getLogger(FrameworkListener.class).info("Finished suite '{}'", suite.getName());
    }

    @Override
    public void onTestStart(ITestResult result) {
        if (!isCucumberRunner(result)) ReportManager.onStart(result);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        if (!isCucumberRunner(result)) ReportManager.onSuccess(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (!isCucumberRunner(result)) ReportManager.onFailure(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (!isCucumberRunner(result)) ReportManager.onSkipped(result);
    }

    private static boolean isCucumberRunner(ITestResult result) {
        return "com.automation.tests.cucumber.CucumberTestRunner".equals(result.getTestClass().getName());
    }
}
