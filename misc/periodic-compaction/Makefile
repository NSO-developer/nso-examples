all:
	mkdir nso-lab-rundir
	ncs-setup --dest ./nso-lab-rundir
	ncs_conf_tool -a "<persistence><format>in-memory-v1</format></persistence>" ncs-config cdb < nso-lab-rundir/ncs.conf > nso-lab-rundir/ncs.conf.tmp
	mv nso-lab-rundir/ncs.conf.tmp nso-lab-rundir/ncs.conf
	cp -r compaction-task nso-lab-rundir/packages/compaction-task
	cp -r ncs.conf.d nso-lab-rundir/
	${MAKE} -C nso-lab-rundir/packages/compaction-task/src/

clean:
	-rm -rf nso-lab-rundir

start:
	cd nso-lab-rundir && ncs

stop:
	-ncs --stop

listener:
	./compaction_notification_listener.py

showcase:
	./showcase.sh
