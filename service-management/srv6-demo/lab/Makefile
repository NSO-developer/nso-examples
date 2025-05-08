XRD_IMAGE ?= xrd-control-plane:latest-24.4
LAB_VARIANT ?= bridge


all: lab.yml

lab.yml: topology.$(LAB_VARIANT).yml
	cp $< lab.yml

topology.bridge.yml: docker-compose.xr.yml
	xr-compose --input-file $< --output-file $@ --image $(XRD_IMAGE)

topology.macvlan.yml: docker-compose.xr.yml macvlan.yml
	cat $^ > $@.in
	xr-compose --input-file $@.in --output-file $@.out --image $(XRD_IMAGE)
	sed -e 's/:xr-[0-9]*,xr_name=Mg0/:eth0,xr_name=Mg0/g' $@.out >$@
	rm $@.in $@.out

start: lab.yml
	docker-compose -f lab.yml up --detach

pause: lab.yml
	docker-compose -f lab.yml down

stop: lab.yml
	docker-compose -f lab.yml down --volumes
