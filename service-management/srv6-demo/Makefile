xrd:
	$(MAKE) -C lab/ start
	$(MAKE) -C ./nso start

netsim:
	$(MAKE) -C labsim/ start
	$(MAKE) -C ./nso start
	$(MAKE) -C ./nso add-labsim

stop-xrd:
	$(MAKE) -C ./nso stop
	$(MAKE) -C lab/ stop

stop-netsim:
	$(MAKE) -C ./nso stop
	$(MAKE) -C labsim/ stop

clean:
	$(MAKE) -C ./nso clean
	$(MAKE) -C ./labsim clean

demo-ns:
	./demo_ns.sh

demo-ns-nonstop:
	NONINTERACTIVE=1 ./demo_ns.sh