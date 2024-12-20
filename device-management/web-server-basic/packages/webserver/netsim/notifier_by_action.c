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
#include <signal.h>

#include <confd_lib.h>
#include <confd_dp.h>

#include "notif.h"
#include "notifctrl.h"

#define OK(val) (assert((val) == CONFD_OK))

static int ctlsock, workersock;
static struct confd_daemon_ctx *dctx;
static struct confd_notification_ctx *live_ctx;
static int send_complex_only = 1;
static int n_notifs;

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

static void getdatetime(struct confd_datetime *datetime)
{
    struct tm tm;
    struct timeval tv;

    gettimeofday(&tv, NULL);
    gmtime_r(&tv.tv_sec, &tm);

    memset(datetime, 0, sizeof(*datetime));
    datetime->year = 1900 + tm.tm_year;
    datetime->month = tm.tm_mon + 1;
    datetime->day = tm.tm_mday;
    datetime->sec = tm.tm_sec;
    datetime->micro = tv.tv_usec;
    datetime->timezone = 0;
    datetime->timezone_minutes = 0;
    datetime->hour = tm.tm_hour;
    datetime->min = tm.tm_min;
}

static void send_notification(confd_tag_value_t *vals, int nvals)
{
    struct confd_datetime eventTime;

    getdatetime(&eventTime);
    OK(confd_notification_send(live_ctx, &eventTime, vals, nvals));
}

static void send_notifup_1(char *name, int flags1, int flags2)
{
    confd_tag_value_t vals[9];
    int i = 0;

    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_linkUp,       notif__ns);  i++;
    CONFD_SET_TAG_STR(&vals[i],      notif_ifName,       name);       i++;
    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_linkProperty, notif__ns);  i++;
    CONFD_SET_TAG_UINT32(&vals[i],   notif_flags,        flags1);     i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_linkProperty, notif__ns);  i++;
    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_linkProperty, notif__ns);  i++;
    CONFD_SET_TAG_UINT32(&vals[i],   notif_flags,        flags2);     i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_linkProperty, notif__ns);  i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_linkUp,       notif__ns);  i++;
    send_notification(vals, i);
}

static void send_startup(char *name)
{
    confd_tag_value_t vals[3];
    int i = 0;

    fprintf(stderr, "sending startup: %s\n", name);
    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_startUp,       notif__ns);  i++;
    CONFD_SET_TAG_STR(&vals[i],      notif_node_id,       name);       i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_startUp,       notif__ns);  i++;
    send_notification(vals, i);
}


static void send_notifup_2(char *name, int flags1, int val1, int val2)
{
    confd_tag_value_t vals[25];
    int i = 0;

    confd_value_t ip;
    confd_value_t mask;

    struct in_addr ia;
    struct in_addr iam;
    ia.s_addr = inet_addr("192.168.128.55");
    iam.s_addr = inet_addr("255.255.255.0");
    CONFD_SET_IPV4(&ip, ia);
    CONFD_SET_IPV4(&mask, iam);

    confd_value_t list[3];
    CONFD_SET_ENUM_HASH(&list[0], notif_UP);
    CONFD_SET_ENUM_HASH(&list[1], notif_BROADCAST);
    CONFD_SET_ENUM_HASH(&list[2], notif_MULTICAST);

    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_linkUp,       notif__ns);  i++;
    CONFD_SET_TAG_STR(&vals[i],      notif_ifName,       name);       i++;
    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_linkProperty, notif__ns);  i++;
    CONFD_SET_TAG_XMLTAG(&vals[i],   notif_newlyAdded,   notif__ns);  i++;
    CONFD_SET_TAG_UINT32(&vals[i],   notif_flags,        flags1);     i++;
    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_extensions,   notif__ns);  i++;
    CONFD_SET_TAG_UINT32(&vals[i],   notif_name,         1);          i++;
    CONFD_SET_TAG_UINT32(&vals[i],   notif_value,        val1);       i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_extensions,   notif__ns);  i++;
    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_extensions,   notif__ns);  i++;
    CONFD_SET_TAG_UINT32(&vals[i],   notif_name,         2);          i++;
    CONFD_SET_TAG_UINT32(&vals[i],   notif_value,        val2);       i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_extensions,   notif__ns);  i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_linkProperty, notif__ns);  i++;

    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_address,   notif__ns);  i++;
    CONFD_SET_TAG_IPV4(&vals[i],     notif_ip,               ia);  i++;
    CONFD_SET_TAG_IPV4(&vals[i],     notif_mask,            iam);  i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_address,   notif__ns);  i++;

    //CONFD_SET_TAG_LIST(&vals[i],     notif_iface_flags, &list[0], 3); i++;

    CONFD_SET_TAG_XMLEND(&vals[i],   notif_linkUp,       notif__ns);  i++;
    send_notification(vals, i);
}

static void send_notifdown(char *name)
{
    confd_tag_value_t vals[3];
    int i = 0;

    CONFD_SET_TAG_XMLBEGIN(&vals[i], notif_linkDown,     notif__ns);  i++;
    CONFD_SET_TAG_STR(&vals[i],      notif_ifName ,      name);       i++;
    CONFD_SET_TAG_XMLEND(&vals[i],   notif_linkDown,     notif__ns);  i++;
    send_notification(vals, i);
}


int init_action(struct confd_user_info *uinfo)
{
    fprintf(stderr, "INIT ACTION CALLED\n");
    confd_action_set_fd(uinfo, workersock);
    fprintf(stderr, "INIT ACTION CALLED DONE\n");
    return CONFD_OK;
}


static int do_action(struct confd_user_info *uinfo,
                     struct xml_tag *name,
                     confd_hkeypath_t *kp,
                     confd_tag_value_t *params,
                     int n)
{
    fprintf(stderr, "RUN ACTION CALLED\n");
    switch (name->tag) {
    case notifctrl_linkUp:
        send_notifup_1("eth1", 2112, 32);
        break;
    default:
        send_notifup_1("eth1", 2112, 32);
        break;
    }
    fprintf(stderr, "RUN ACTION CALLED DONE\n");
    return CONFD_OK;
}

static int abort_action(struct confd_user_info *uinfo) {
    fprintf(stderr, "Aborting outstanding action\n");
    /* We need to clean  up the worker socket by replying */
    confd_action_delayed_reply_error(uinfo, "aborted");
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
    struct confd_notification_stream_cbs ncb;
    struct confd_action_cbs acb;
    struct pollfd set[3];
    int ret;
    int use_actions = 0;

    snprintf(confd_port, sizeof(confd_port), "%d", CONFD_PORT);
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = PF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;

    while ((c = getopt(argc, argv, "Sfdtprc:s:Ca")) != -1) {
        switch (c) {
        case 'f':
            foreground = 1;
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


    fprintf(stderr, "Using actions %i\n", use_actions);

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
        confd_fatal("Failed to connect to ConfD\n");
    if ((workersock = get_workersock(addr)) < 0)
        confd_fatal("Failed to connect to ConfD\n");

    memset(&ncb, 0, sizeof(ncb));
    ncb.fd = workersock;
    ncb.get_log_times = NULL;
    ncb.replay = NULL;
    strcpy(ncb.streamname, "interface");
    ncb.cb_opaque = NULL;
    if (confd_register_notification_stream(dctx, &ncb, &live_ctx) !=
            CONFD_OK) {
        confd_fatal("Couldn't register stream %s\n", ncb.streamname);
    }


    /* register the action handler callback */
    memset(&acb, 0, sizeof(acb));
    strcpy(acb.actionpoint, "send-point");
    acb.init = init_action;
    acb.action = do_action;
    acb.abort = abort_action;

    if (confd_register_action_cbs(dctx, &acb) != CONFD_OK) {
        confd_fatal("Couldn't register action callbacks");
    }

    if (confd_register_done(dctx) != CONFD_OK)
        confd_fatal("Failed to complete registration \n");
    fflush(stdout);


    if (!foreground) {
        if (fork()) {
            /* parent */
            _exit(0);
        }
    }

    if (!foreground) {
        /* print PID _after_ signal handler has been setup
           to ensure signals sents are handled properly. */
        fprintf(stderr, "PID = %d\n", getpid());
    }

    while (1) {

        set[0].fd = ctlsock;
        set[0].events = POLLIN;
        set[0].revents = 0;

        set[1].fd = workersock;
        set[1].events = POLLIN;
        set[1].revents = 0;

        if (poll(set, 2, 1) < 0) {
            confd_fatal("Poll failed");
        }

        /* Check for I/O */
        if (set[0].revents & POLLIN) { /* ctlsock */
            fprintf(stderr, "DOING CTLSOCK\n");
            if ((ret = confd_fd_ready(dctx, ctlsock)) == CONFD_EOF) {
                exit(0);
            } else if (ret == CONFD_ERR &&
                       confd_errno != CONFD_ERR_EXTERNAL) {
                confd_fatal("Error on control socket request: "
                            "%s (%d): %s\n", confd_strerror(confd_errno),
                            confd_errno, confd_lasterr());
            }
        }

        if (set[1].revents & POLLIN) { /* workersock */
            fprintf(stderr, "DOING ACTIONS\n");
            if ((ret = confd_fd_ready(dctx, workersock)) == CONFD_EOF) {
                fprintf(stderr, "Worker socket closed\n");
                exit(0);
            } else if (ret == CONFD_ERR && confd_errno != CONFD_ERR_EXTERNAL) {
                confd_fatal("Error on worker socket request: %s (%d): %s",
                            confd_strerror(confd_errno),
                            confd_errno, confd_lasterr());
            }
        }
    }
}
