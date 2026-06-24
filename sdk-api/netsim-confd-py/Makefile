demo:
	./demo.sh

demo-maapi:
	python3 demo_maapi.py

demo-maapi-nonstop:
	NONINTERACTIVE=1 python3 demo_maapi.py

stop:
	-ncs --stop
	-ncs-netsim --dir nso-rundir/netsim stop

clean:
	rm -rf nso-rundir
