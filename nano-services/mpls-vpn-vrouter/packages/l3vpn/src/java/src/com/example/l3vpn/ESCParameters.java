package com.example.l3vpn;

public class ESCParameters {
  public String service_name;
  public String service_version;
  public String vm_group;

  public ESCParameters(String service_name, String service_version,
      String vm_group) {
    this.service_name = service_name;
    this.service_version = service_version;
    this.vm_group = vm_group;
  }

  public ESCParameters() {
    service_name = Settings.defaultEscServiceName;
    service_version = Settings.defaultEscServiceVersion;
    vm_group = Settings.defaultEscVMGroup;
  }

  public String toString() {
    return String
        .format("%s(%s) - %s", service_name, service_version, vm_group);
  }
}