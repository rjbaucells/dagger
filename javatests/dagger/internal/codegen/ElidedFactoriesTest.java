/*
 * Copyright (C) 2017 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ElidedFactoriesTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ElidedFactoriesTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void simpleComponent() throws Exception {
    JavaFileObject injectedType =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class InjectedType {",
            "  @Inject InjectedType() {}",
            "}");

    JavaFileObject dependsOnInjected =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class DependsOnInjected {",
            "  @Inject DependsOnInjected(InjectedType injected) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  DependsOnInjected dependsOnInjected();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectedType, dependsOnInjected, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }

  @Test
  public void simpleComponent_injectsProviderOf_dependsOnScoped() throws Exception {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");

    JavaFileObject needsProvider =
        JavaFileObjects.forSourceLines(
            "test.NeedsProvider",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class NeedsProvider {",
            "  @Inject NeedsProvider(Provider<DependsOnScoped> provider) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  NeedsProvider needsProvider();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, needsProvider);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }

  @Test
  public void scopedBinding_onlyUsedInSubcomponent() throws Exception {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  Sub sub();",
            "}");
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  DependsOnScoped dependsOnScoped();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, subcomponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }
}
