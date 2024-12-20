
GEN_ALARM=./generate-alarm.sh

clean:



alarm-1:
	$(GEN_ALARM) \
		--device c0 \
        --object /devices/device{c0}/config/ios:interface/FastEthernet{1/0}/switchport \
		--type spanning-tree-alarm \
		--specific-problem "spanning-tree" \
		--severity critical \
        --text "Spanning Tree BPDU received on edge interface. Interface set to errdisable state."



alarm-2:
	$(GEN_ALARM) \
		--device c1 \
        --object /devices/device{c1}/config/ios:interface/FastEthernet{1/1}/switchport \
		--type unidirectional-alarm \
		--specific-problem "unidirectional" \
		--severity major \
        --text "Unidirectional link detected, port disabled." 


clear-1:
	$(GEN_ALARM) \
		--device c0 \
        --object /devices/device{c0}/config/ios:interface/FastEthernet{1/0}/switchport \
		--type spanning-tree-alarm \
		--specific-problem "spanning-tree" \
		--severity cleared \
        --text "Spanning Tree errdisable state recovered."


clear-2:
	$(GEN_ALARM) \
		--device c1 \
        --object /devices/device{c1}/config/ios:interface/FastEthernet{1/1}/switchport \
		--type unidirectional-alarm \
		--specific-problem "unidirectional" \
		--severity cleared \
        --text "Port enabled."


