#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/poll.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <stdarg.h>
#include <stdio.h>

#include "confd_lib.h"
#include "confd_dp.h"
#include "confd_maapi.h"

/* include generated ns file */
#include "BASIC-CONFIG-MIB.h"

int debuglevel = CONFD_SILENT;

static int ctlsock;
static int workersock;
static int maapi_socket;
static struct confd_daemon_ctx *dctx;

struct confd_trans_validate_cbs vcb;
struct confd_valpoint_cb valp1;

static void OK(int rval)
{
    if (rval != CONFD_OK) {
        fprintf(stderr, "validate.c: error not CONFD_OK: %d : %s \n",
                confd_errno, confd_lasterr());
        abort();
    }
}

static int init_validation(struct confd_trans_ctx *tctx)
{
    OK(maapi_attach(maapi_socket, BASIC_CONFIG_MIB__ns, tctx));
    confd_trans_set_fd(tctx, workersock);
    return CONFD_OK;
}

static int stop_validation(struct confd_trans_ctx *tctx)
{
    OK(maapi_detach(maapi_socket, tctx));
    return CONFD_OK;
}

static int validate(struct confd_trans_ctx *tctx,
                    confd_hkeypath_t *kp,
                    confd_value_t *newval)
{
    int v;
    int rtid, ret;

    switch (CONFD_GET_XMLTAG(&(kp->v[0][0]))) {
    case BASIC_CONFIG_MIB_bscActFlow:
        /* check that admin status is locked in running */
        rtid = maapi_start_trans(maapi_socket, CONFD_RUNNING, CONFD_READ);
        ret = maapi_get_enum_hash_elem(maapi_socket, rtid, &v,
                                       "%*h/bscActAdminState", kp->len - 1, kp);
        if (ret == CONFD_OK && v != BASIC_CONFIG_MIB_locked) {
            confd_trans_seterr(tctx, "Admin state is not locked");
            maapi_finish_trans(maapi_socket, rtid);
            return CONFD_ERR;
        }
        maapi_finish_trans(maapi_socket, rtid);
        break;
    case BASIC_CONFIG_MIB_bscBaseErr:
        v = CONFD_GET_INT32(newval);
        if (v == 0) {
            return CONFD_OK;
        } else if (v == 1) {
            confd_trans_seterr_extended(tctx, CONFD_ERRCODE_INCONSISTENT_VALUE,
                                        0, 0, "base Err 1 is invalid");
            return CONFD_ERR;
        } else if (v == 2) {
            confd_trans_seterr_extended(tctx, CONFD_ERRCODE_RESOURCE_DENIED,
                                        0, 0, "base Err 2 resource denied");
            return CONFD_ERR;
        }
        confd_trans_seterr(tctx, "General error");
        return CONFD_ERR;
        break;
    }

    return CONFD_OK;
}

static int maapi_sock(int *maapi_sock, int port)
{

    struct sockaddr_in addr;

    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);

    if ((*maapi_sock = socket(PF_INET, SOCK_STREAM, 0)) < 0 )
        confd_fatal("Failed to open socket\n");

    if (maapi_connect(*maapi_sock, (struct sockaddr*)&addr,
                      sizeof (struct sockaddr_in)) < 0)
        confd_fatal("Failed to confd_connect() to confd \n");

    return CONFD_OK;
}

int main(int argc, char **argv)
{
    int c, port = 0;

    struct sockaddr_in addr;

    while ((c = getopt(argc, argv, "tdpsP:")) != -1) {
        switch(c) {
        case 't':
            debuglevel = CONFD_TRACE;
            break;
        case 'd':
            debuglevel = CONFD_DEBUG;
            break;
        case 'p':
            debuglevel = CONFD_PROTO_TRACE;
            break;
        case 's':
            debuglevel = CONFD_SILENT;
            break;
        case 'P':
            port = atoi(optarg);
            break;
        }
    }

    confd_init("validate", stderr, debuglevel);

    if ((dctx = confd_init_daemon("mydaemon")) == NULL)
        confd_fatal("Failed to initialize confd\n");

    if ((ctlsock = socket(PF_INET, SOCK_STREAM, 0)) < 0 )
        confd_fatal("Failed to open ctlsocket\n");

    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);

    OK(confd_load_schemas((struct sockaddr*)&addr, sizeof(struct sockaddr_in)));

    /* Create the first control socket, all requests to */
    /* create new transactions arrive here */
    if (confd_connect(dctx, ctlsock, CONTROL_SOCKET,
                      (struct sockaddr*)&addr,
                      sizeof (struct sockaddr_in)) < 0) {
        confd_fatal("Failed to confd_connect() to confd \n");
    }

    /* Also establish a workersocket, this is the most simple */
    /* case where we have just one ctlsock and one workersock */
    if ((workersock = socket(PF_INET, SOCK_STREAM, 0)) < 0 )
        confd_fatal("Failed to open workersocket\n");
    if (confd_connect(dctx, workersock, WORKER_SOCKET,(struct sockaddr*)&addr,
                      sizeof (struct sockaddr_in)) < 0)
        confd_fatal("Failed to confd_connect() to confd \n");

    vcb.init = init_validation;
    vcb.stop = stop_validation;
    confd_register_trans_validate_cb(dctx, &vcb);

    valp1.validate = validate;
    strcpy(valp1.valpoint, "basic_vp");
    OK(confd_register_valpoint_cb(dctx, &valp1));

    OK(confd_register_done(dctx));

    OK(maapi_sock(&maapi_socket, port));

    while (1) {
        struct pollfd set[2];
        int ret;

        set[0].fd = ctlsock;
        set[0].events = POLLIN;
        set[0].revents = 0;

        set[1].fd = workersock;
        set[1].events = POLLIN;
        set[1].revents = 0;

        if (poll(&set[0], 2, -1) < 0) {
            perror("Poll failed:");
            continue;
        }

        if (set[0].revents & POLLIN) {
            if ((ret = confd_fd_ready(dctx, ctlsock)) == CONFD_EOF) {
                exit(0);
            } else if (ret == CONFD_ERR && confd_errno != CONFD_ERR_EXTERNAL) {
                exit(1);
            }
        }
        if (set[1].revents & POLLIN) {
            if ((ret = confd_fd_ready(dctx, workersock)) == CONFD_EOF) {
                exit(0);
            } else if (ret == CONFD_ERR && confd_errno != CONFD_ERR_EXTERNAL) {
                exit(0);
            }
        }
    }

}
