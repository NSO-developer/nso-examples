packages-%:
	$(MAKE) -C packages/encrypted/src $*

all:	ncs.conf ncs.encryptedStrings packages-all

ncs.conf:
	ncs-setup --dest . && \
	ncs_conf_tool -e "<external-keys> \
    <command>./external_crypto_keys.py</command> \
    <command-argument>ncs.encryptedStrings</command-argument> \
  </external-keys>" ncs-config encrypted-strings < ncs.conf > ncs.conf.tmp && \
	mv -f ncs.conf.tmp ncs.conf

gen_random=$(shell dd if=/dev/urandom bs=1 count=$1 2>/dev/null | od -t x | \
			awk '{for(i=2;i<=NF;i++)printf "%s",$$i}')

ncs.encryptedStrings:
	@echo "DES3CBC_KEY1=$(call gen_random,8)" > ncs.encryptedStrings && \
	echo "DES3CBC_KEY2=$(call gen_random,8)" >> ncs.encryptedStrings && \
	echo "DES3CBC_KEY3=$(call gen_random,8)" >> ncs.encryptedStrings && \
	echo "AESCFB128_KEY=$(call gen_random,16)" >> ncs.encryptedStrings && \
	echo "AES256CFB128_KEY=$(call gen_random,32)" >> ncs.encryptedStrings

clean: packages-clean
	$(RM) ncs.conf ncs.encryptedStrings README.ncs storedstate ./*.log
	$(RM) -r logs ncs-cdb scripts state

demo:
	./demo.sh
