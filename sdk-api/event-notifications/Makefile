######################################################################
# NSO event notifications example
#
# See the README file for more information
######################################################################
demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh

all:
	mkdir -p nso-rundir/packages

stop:
	-ncs-netsim --dir nso-rundir/netsim stop
	-kill $$(cat nso-rundir/notif-app.pid)
	-ncs --stop
	-kill $$(cat nso-rundir/tailf.pid)

clean:
	rm -rf nso-rundir
