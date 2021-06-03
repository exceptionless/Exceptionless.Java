package com.exceptionless.exceptionlessclient.configuration;

import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class PluginManagerTest {
  private PluginManager pluginManager;

  @BeforeEach
  public void setup() {
    pluginManager = PluginManager.builder().build();
  }

  @Test
  public void itCanConfigureDefaultPlugins() {
    List<String> pluginNames =
        List.of(
            "ConfigurationDefaultsPlugin",
            "ErrorPlugin",
            "DuplicateCheckerPlugin",
            "EventExclusionPlugin",
            "ModuleInfoPlugin",
            "RequestInfoPlugin",
            "EnvironmentInfoPlugin",
            "SubmissionMethodPlugin");
    pluginNames.forEach(
        pluginName ->
            assertThat(
                    pluginManager.getPlugins().stream()
                        .anyMatch(plugin -> plugin.getName().contains(pluginName)))
                .isTrue());
  }

  @Test
  public void itCanSortThemAccordingToPriority() {
    List<EventPluginIF> plugins = pluginManager.getPlugins();
    for (int i = 1; i < plugins.size(); i++) {
      if (plugins.get(i - 1).getPriority() < plugins.get(i).getPriority()) {
        fail("The plugins should be in non increasing order");
      }
    }
  }

  @Test
  public void itCanAddPluginWhichIsInsertedInSortedOrder() {
    pluginManager.addPlugin(
        new EventPluginIF() {
          @Override
          public int getPriority() {
            return Integer.MAX_VALUE;
          }

          @Override
          public String getName() {
            return "test-plugin";
          }

          @Override
          public void run(
              EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {}
        });

    assertThat(pluginManager.getPlugins().get(0).getName()).isEqualTo("test-plugin");
  }

  @Test
  public void itCanAddAndRemovePlugins() {
    pluginManager.addPlugin(
        new EventPluginIF() {
          @Override
          public int getPriority() {
            return 10;
          }

          @Override
          public String getName() {
            return "test-plugin";
          }

          @Override
          public void run(
              EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {}
        });
    assertThat(
            pluginManager.getPlugins().stream()
                .anyMatch(plugin -> plugin.getName().equals("test-plugin")))
        .isTrue();

    pluginManager.removePlugin("test-plugin");
    assertThat(
            pluginManager.getPlugins().stream()
                .anyMatch(plugin -> plugin.getName().equals("test-plugin")))
        .isFalse();
  }
}
