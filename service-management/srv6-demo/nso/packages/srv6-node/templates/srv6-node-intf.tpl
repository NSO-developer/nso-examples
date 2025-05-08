<config-template xmlns="http://tail-f.com/ns/config/1.0">
  <devices xmlns="http://tail-f.com/ns/ncs">
    <device>
      <name>{name}</name>
      <config>
        <interface xmlns="http://tail-f.com/ned/cisco-ios-xr">
          <GigabitEthernet>
            <id>{$INTERFACE_NO}</id>
            <ipv6>
              <enable/>
            </ipv6>
            <?if {$USE_CDP}?>
              <cdp/>
            <?end?>
            <shutdown tags="delete"/>
          </GigabitEthernet>
        </interface>
        <router xmlns="http://tail-f.com/ned/cisco-ios-xr">
          <isis>
            <tag>
              <name>1</name>
              <interface>
                <name>GigabitEthernet{$INTERFACE_NO}</name>
                <point-to-point/>
                <address-family>
                  <ipv6>
                    <unicast>
                      <?if {$USE_FRR}?>
                        <fast-reroute>
                          <enable>
                            <per-prefix/>
                          </enable>
                          <per-prefix>
                            <ti-lfa/>
                          </per-prefix>
                        </fast-reroute>
                      <?end?>
                    </unicast>
                  </ipv6>
                </address-family>
              </interface>
            </tag>
          </isis>
        </router>
      </config>
    </device>
  </devices>
</config-template>
