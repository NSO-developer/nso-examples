DOCKER_CONTAINER_NAME ?= nso-ldap-example
DOCKER_OPTS ?= --ulimit nofile=1024
SLAPD_OPTS ?= -d 1 -h "ldap:/// ldapi:///"

.PHONY: all
all: .image-ready

.image-ready: Dockerfile
	docker build $(DOCKER_OPTS) -t $(DOCKER_CONTAINER_NAME) .
	@touch $@

.PHONY: start
start: .image-ready
	# Run OpenLDAP container in the foreground
	docker run -d -p 1389:389 $(DOCKER_OPTS) --name $(DOCKER_CONTAINER_NAME) \
		$(DOCKER_CONTAINER_NAME) slapd $(SLAPD_OPTS)

.PHONY: stop
stop:
	docker rm -f $(DOCKER_CONTAINER_NAME) || true

.PHONY: clean
clean:
	rm -f .image-ready
