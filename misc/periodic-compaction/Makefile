all:
	mkdir nso-lab-rundir
	ncs-setup --dest ./nso-lab-rundir
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
