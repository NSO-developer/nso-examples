#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/poll.h>
#include <time.h>
#include <sys/time.h>

#include <confd_lib.h>
#include <confd_dp.h>

#include "interfaces.h"

#define OK(val) (assert((val) == CONFD_OK))

static int ctlsock, workersock;
static struct confd_daemon_ctx *dctx;
/* Our daemon context as a global variable */
static struct confd_daemon_ctx *dctx;
static int ctlsock;
static int workersock;
static int receive = 2;
static int transmit = 1;

static int get_ctlsock(struct addrinfo *addr)
{
    int sock;

    if ((sock =
         socket(addr->ai_family, addr->ai_socktype, addr->ai_protocol)) < 0)
        return -1;
    if (confd_connect(dctx, sock, CONTROL_SOCKET,
                      addr->ai_addr, addr->ai_addrlen) != CONFD_OK) {
        close(sock);
        return -1;
    }
    return sock;
}

static int get_workersock(struct addrinfo *addr)
{
     int sock;

     if ((sock =
          socket(addr->ai_family, addr->ai_socktype, addr->ai_protocol)) < 0)
          return -1;
     if (confd_connect(dctx, sock, WORKER_SOCKET,
                       addr->ai_addr, addr->ai_addrlen) != CONFD_OK) {
          close(sock);
          return -1;
     }
     return sock;
}

static int s_init(struct confd_trans_ctx *tctx)
{
    confd_trans_set_fd(tctx, workersock);
    return CONFD_OK;
}

static int s_finish(struct confd_trans_ctx *tctx)
{
    return CONFD_OK;
}

static int get_elem(struct confd_trans_ctx *tctx,
                    confd_hkeypath_t *keypath)
{
    confd_value_t v;

    if (CONFD_GET_XMLTAG(&(keypath->v[1][0])) == if_transmit) {
      CONFD_SET_UINT32(&v, transmit);
      transmit += 2;
    }
    else if (CONFD_GET_XMLTAG(&(keypath->v[1][0])) == if_receive) {
        if (CONFD_GET_XMLTAG(&(keypath->v[0][0])) == if_dropped) {
            CONFD_SET_UINT32(&v, receive/12);
        } else {
            CONFD_SET_UINT32(&v, receive);
        }
        receive +=2;
    }
    confd_data_reply_value(tctx, &v);
    return CONFD_OK;
}

int main(int argc, char **argv)
{
     char confd_port[16];
     struct addrinfo hints;
     struct addrinfo *addr = NULL;
     int foreground = 0;
     int debuglevel = CONFD_SILENT;
     int i;
     int c;
     char *p, *dname;

     struct confd_trans_cbs trans;
     struct confd_data_cbs data;

     memset(&trans, 0, sizeof (struct confd_trans_cbs));
     trans.init = s_init;
     trans.finish = s_finish;

     memset(&data, 0, sizeof (struct confd_data_cbs));
     data.get_elem = get_elem;
     strcpy(data.callpoint, if__callpointid_if_stats);

     snprintf(confd_port, sizeof(confd_port), "%d", CONFD_PORT);
     memset(&hints, 0, sizeof(hints));
     hints.ai_family = PF_UNSPEC;
     hints.ai_socktype = SOCK_STREAM;

     while ((c = getopt(argc, argv, "fdtprc:")) != -1) {
          switch (c) {
          case 'f':
              foreground++;
              break;
          case 'd':
               debuglevel = CONFD_DEBUG;
               break;
          case 't':
               debuglevel = CONFD_TRACE;
               break;
          case 'p':
               debuglevel = CONFD_PROTO_TRACE;
               break;
          case 'c':
               if ((p = strchr(optarg, '/')) != NULL)
                    *p++ = '\0';
               else
                    p = confd_port;
               if (getaddrinfo(optarg, p, &hints, &addr) != 0) {
                    if (p != confd_port) {
                         *--p = '/';
                         p = "/port";
                    } else {
                         p = "";
                    }
                    fprintf(stderr, "%s: Invalid address%s: %s\n",
                            argv[0], p, optarg);
                    exit(1);
               }
               break;
          default:
               fprintf(stderr,
                       "Usage: %s [-dtpr] [-c address[/port]] [-s Secs]\n",
                       argv[0]);
               exit(1);
          }
     }

     if (addr == NULL &&
         ((i = getaddrinfo("127.0.0.1", confd_port, &hints, &addr)) != 0))
          /* "Can't happen" */
          confd_fatal("%s: Failed to get address for ConfD: %s\n",
                      argv[0], gai_strerror(i));
     if ((dname = strrchr(argv[0], '/')) != NULL)
          dname++;
     else
          dname = argv[0];
     /* Init library */
     confd_init(dname, stderr, debuglevel);

     if ((dctx = confd_init_daemon(dname)) == NULL)
          confd_fatal("Failed to initialize ConfD\n");
     if ((ctlsock = get_ctlsock(addr)) < 0)
        confd_fatal("Failed to connect ctlsock to ConfD\n");
     if ((workersock = get_workersock(addr)) < 0)
          confd_fatal("Failed to connect worker to ConfD\n");


     if (confd_register_trans_cb(dctx, &trans) == CONFD_ERR)
       confd_fatal("Failed to register trans cb \n");

     if (confd_register_data_cb(dctx, &data) == CONFD_ERR)
       confd_fatal("Failed to register data cb \n");

     if (confd_register_done(dctx) != CONFD_OK)
         confd_fatal("Failed to complete registration \n");

     fflush(stdout);

     if (!foreground) {
         if (fork()) {
             /* parent */
             _exit(0);
         }
         printf("PID = %d\n", getpid());
     }

     while(1) {
       struct pollfd set[2];
       int ret;

       set[0].fd = ctlsock;
       set[0].events = POLLIN;
       set[0].revents = 0;

       set[1].fd = workersock;
       set[1].events = POLLIN;
       set[1].revents = 0;

       if (poll(set, sizeof(set)/sizeof(*set), -1) < 0) {
         perror("Poll failed:");
         continue;
       }

       /* Check for I/O */
       if (set[0].revents & POLLIN) {
         if ((ret = confd_fd_ready(dctx, ctlsock)) == CONFD_EOF) {
             exit(0);
         } else if (ret == CONFD_ERR && confd_errno != CONFD_ERR_EXTERNAL) {
           confd_fatal("Error on control socket request: %s (%d): %s\n",
                       confd_strerror(confd_errno),confd_errno,confd_lasterr());
         }
       }
       if (set[1].revents & POLLIN) {
         if ((ret = confd_fd_ready(dctx, workersock)) == CONFD_EOF) {
             exit(0);
         } else if (ret == CONFD_ERR && confd_errno != CONFD_ERR_EXTERNAL) {
            confd_fatal("Error on worker socket request: %s (%d): %s\n",
                       confd_strerror(confd_errno),confd_errno,confd_lasterr());
         }
       }
     }
}
