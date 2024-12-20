

all: device-common
	(cd device-x1 && ant compile)
	(cd device-x2 && ant compile)
	(cd device-x3 && ant compile)

device-common::
	(cd device-common && ant all)

clean:
	(cd device-x1 && ant clean)
	(cd device-x2 && ant clean)
	(cd device-x3 && ant clean)
	(cd device-common && ant clean)

start-x%:
	cd device-x$*; ./start-device.sh &

stop-x1:
	lsof -w -n -i tcp:8045 | awk '/^java/{print $$2; exit}' | xargs kill 2>/dev/null || true

stop-x2:
	lsof -w -n -i tcp:8046 | awk '/^java/{print $$2; exit}' | xargs kill 2>/dev/null || true

stop-x3:
	lsof -w -n -i tcp:8047 | awk '/^java/{print $$2; exit}' | xargs kill 2>/dev/null || true


start-devices:
	for i in 1 2 3; do \
	($(MAKE) start-x$${i} ); \
		done
stop-devices:
	for i in 1 2 3; do \
	($(MAKE) stop-x$${i}); \
		done
