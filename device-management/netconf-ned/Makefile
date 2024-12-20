######################################################################
# Example setting up a NETCONF device with NSO
#
# See the README file for more information
######################################################################
clean:
	-make -C devsim clean
	-rm -rf dev-yang nso-rundir

stop:
	-ncs --stop
	-make -C devsim stop
	@echo "##### All NSO and devsim instances stopped"

all:
	@echo "##### Built by the demo script"

start: demo
demo:
	./demo.sh

demo-nb:
	./demo_nb.sh
