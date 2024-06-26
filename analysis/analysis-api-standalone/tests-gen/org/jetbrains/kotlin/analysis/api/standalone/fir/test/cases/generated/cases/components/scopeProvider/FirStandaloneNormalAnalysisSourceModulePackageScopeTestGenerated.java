/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.standalone.fir.test.cases.generated.cases.components.scopeProvider;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.standalone.fir.test.configurators.AnalysisApiFirStandaloneModeTestConfiguratorFactory;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiTestConfiguratorFactoryData;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiTestConfigurator;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.TestModuleKind;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.FrontendKind;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisSessionMode;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiMode;
import org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.scopeProvider.AbstractPackageScopeTest;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.analysis.api.GenerateAnalysisApiTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/analysis-api/testData/components/scopeProvider/packageScope")
@TestDataPath("$PROJECT_ROOT")
public class FirStandaloneNormalAnalysisSourceModulePackageScopeTestGenerated extends AbstractPackageScopeTest {
  @NotNull
  @Override
  public AnalysisApiTestConfigurator getConfigurator() {
    return AnalysisApiFirStandaloneModeTestConfiguratorFactory.INSTANCE.createConfigurator(
      new AnalysisApiTestConfiguratorFactoryData(
        FrontendKind.Fir,
        TestModuleKind.Source,
        AnalysisSessionMode.Normal,
        AnalysisApiMode.Standalone
      )
    );
  }

  @Test
  public void testAllFilesPresentInPackageScope() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/analysis-api/testData/components/scopeProvider/packageScope"), Pattern.compile("^(.+)\\.kt$"), null, true);
  }

  @Test
  @TestMetadata("callables.kt")
  public void testCallables() {
    runTest("analysis/analysis-api/testData/components/scopeProvider/packageScope/callables.kt");
  }

  @Test
  @TestMetadata("classes.kt")
  public void testClasses() {
    runTest("analysis/analysis-api/testData/components/scopeProvider/packageScope/classes.kt");
  }

  @Test
  @TestMetadata("dependency.kt")
  public void testDependency() {
    runTest("analysis/analysis-api/testData/components/scopeProvider/packageScope/dependency.kt");
  }

  @Test
  @TestMetadata("javaClass.kt")
  public void testJavaClass() {
    runTest("analysis/analysis-api/testData/components/scopeProvider/packageScope/javaClass.kt");
  }

  @Test
  @TestMetadata("library.kt")
  public void testLibrary() {
    runTest("analysis/analysis-api/testData/components/scopeProvider/packageScope/library.kt");
  }

  @Test
  @TestMetadata("simple.kt")
  public void testSimple() {
    runTest("analysis/analysis-api/testData/components/scopeProvider/packageScope/simple.kt");
  }
}
