all:
	mkdir nso-lab-rundir
	ncs-setup --dest ./nso-lab-rundir
	cp init/Makefile.in nso-lab-rundir/Makefile
	cp init/ncs_init.xml.in nso-lab-rundir/ncs-cdb/ncs_init.xml
	cp -r ../../common/packages/router-nc-1.1 nso-lab-rundir/packages/router-nc-1.1

clean:
	-rm -rf nso-lab-rundir

stop:
	-ncs-netsim --dir nso-lab-rundir/netsim -a stop
	-ncs --stop
	@echo "##### All NSO and netsim instances stopped"

showcase:
	./showcase.sh

showcase-rc:
	python3 showcase_rc.py
