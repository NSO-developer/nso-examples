reset-node%:
	( cd node$* && ncs-setup --reset )

start-node%:
	( cd node$* && ncs )

stop-node%:
	NCS_IPC_PATH=/tmp/nso/nso-ipc$* ncs --stop || true

cli-node% cli%:
	NCS_IPC_PATH=/tmp/nso/nso-ipc$* ncs_cli -Cu admin -g admin
