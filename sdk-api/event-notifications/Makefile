######################################################################
# NSO event notifications example
#
# See the README file for more information
######################################################################
demo:
	./demo.sh

demo-maapi:
	python3 demo_maapi.py

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh

demo-maapi-nonstop:
	NONINTERACTIVE=1 python3 demo_maapi.py

all:
	mkdir -p nso-rundir/packages

stop:
	-ncs-netsim --dir nso-rundir/netsim stop
	-kill $$(cat nso-rundir/notif-app.pid)
	-ncs --stop
	-kill $$(cat nso-rundir/tailf.pid)

clean:
	rm -rf nso-rundir
