package tech.stackable.hadoop;

import org.apache.hadoop.conf.Configuration;

public enum HadoopConfigSingleton {
  INSTANCE;
  private final Configuration configuration = new Configuration();

  public Configuration getConfiguration() {
    return this.configuration;
  }
}
