-module(escnotif).
-compile(exportall).

-include_lib("econfd.hrl").
-include("econfd_errors.hrl").
-include("esc_types.hrl").
-on_load(on_load/0).

-define(ESCNS, 'http://www.cisco.com/esc/esc').
-define(NOTNS, 'http://www.cisco.com/esc/esc').

on_load() ->
    proc_lib:spawn(fun start/0),
    proc_lib:spawn(fun printer/0),
    ok.

start() ->
    %% for supervision
    process_flag(trap_exit, true),
    start(0).

start(Restarts) ->
    %timer:sleep(2000),
    NCbs = #confd_notification_stream_cbs{
      streamname = escEvent
     },
    {ok,Daemon} = econfd:init_daemon(enotifier, ?CONFD_TRACE, user, none,
                                     {127,0,0,1}, ?CONFD_PORT),
    {ok, Nctx} = econfd:register_notification_stream(Daemon, NCbs),
    ok = econfd:register_done(Daemon),
    Worker = proc_lib:spawn_link(fun() -> worker(Nctx) end),
    proc_lib:spawn_link(fun() -> tmp_kicker(Nctx) end),
    supervise(Restarts, Worker, Daemon).


%% simple supervision
supervise(Restarts, Worker, Daemon) ->
    receive
        {'EXIT', Daemon, _} when Restarts < 3 ->
            exit(Worker, shutdown),
            start(Restarts + 1);
        {'EXIT', Daemon, _} ->
            exit(too_many_restarts);
        {'EXIT', Worker, _} when Restarts < 3 ->
            exit(Daemon, shutdown),
            start(Restarts + 1);
        {'EXIT', Worker, _} ->
            exit(too_many_restarts)
    after
        timeout(Restarts) ->
            supervise(0, Worker, Daemon)
    end.

timeout(0)         -> infinity;
timeout(_Restarts) -> 10000.


worker(Nctx) ->
    {ok, S} = econfd_cdb:connect({127,0,0,1}, ?CONFD_PORT),
    econfd_cdb:wait_start(S),
    {ok, S2} = econfd_cdb:connect({127,0,0,1}),
    {ok, Sub} = econfd_cdb:subscribe_session(S2),
    P =  "/esc_datamodel/tenants/tenant/services/service_definition/vm_group",
    _X = econfd_cdb:subscribe(Sub, 1, ?ESCNS, P),
    {ok, _} = _X,
    Pong = "/esc_datamodel/pong",
    {ok,_} = econfd_cdb:subscribe(Sub, 1, ?ESCNS, Pong),
    ok = econfd_cdb:subscribe_done(Sub),
    wloop(Nctx, S, S2, Sub).

wloop(Nctx, S, S2, Sub) ->
    Reader = fun(Update) -> reader(Update, S, Sub, Nctx) end,
    try
        econfd_cdb:wait(Sub, 20000, Reader)
    catch X:Y ->
            print("ERR reader died ~p~n", [{X,Y}])
    end,
    wloop(Nctx, S, S2, Sub).


iter(IKP = [pong,['http://www.cisco.com/esc/esc'|esc_datamodel]],
     ?MOP_DELETED,_,_,Acc) ->
    {ok, ?ITER_CONTINUE, [{deleted, IKP}| Acc]};

iter(IKP = [pong,['http://www.cisco.com/esc/esc'|esc_datamodel]],
     _,_,{_,Val},Acc) ->
    {ok, ?ITER_CONTINUE, [{created, Val, IKP}| Acc]};

iter(IKP = [{G}, vm_group, {DepName, SrvName, Version},
            service_definition,
            services ,{Tenant} |_], ?MOP_CREATED, _,_,State) ->
    S2 = [{created, IKP, G, DepName, SrvName, Version, Tenant} | State],
    %list_to_binary([Tenant, ":", G, ":", Vm])} | State],
    {ok, ?ITER_CONTINUE, S2};

iter(IKP = [{Tenant}, tenant |_], ?MOP_DELETED, _,_,State) ->
    S2 = [{deleted, IKP, <<"CSR">>, <<"vpn">>,
           <<"X">>, <<"X">>, Tenant} | State],
    %list_to_binary([Tenant, ":", G, ":", Vm])} | State],
    {ok, ?ITER_CONTINUE, S2};

iter(_IKP, _Op, _OldValue, _Value, State)  ->
    print("skip ~p~n", [_IKP]),
    {ok, ?ITER_RECURSE, State}.

reader(Update, _S , Sub, Nctx) ->
    print("Update = ~p~n", [Update]),
    [Point] = Update,
    Iter = fun(IKP, Op, OldValue, Value, State) ->
                   iter(IKP, Op, OldValue, Value, State)
           end,
    {ok,CreatedOrDeleted} = econfd_cdb:diff_iterate(
                Sub, Point, Iter,
               ?CDB_ITER_WANT_PREV bor ?CDB_ITER_WANT_ANCESTOR_DELETE, []),
    print("Cr = ~p~n", [CreatedOrDeleted]),
    %%timer:sleep(5000),
    proc_lib:spawn(fun() -> send_notif(Nctx, CreatedOrDeleted) end),
    ?CDB_DONE_PRIORITY.

mk_vm_name(Ten, DepName, G) ->
    <<Ten/binary, "_", DepName/binary, "_", G/binary>>.

send_notif(Nctx, [{created, N,
                   _IKP = [pong,['http://www.cisco.com/esc/esc'|esc_datamodel]]}
                  | _Tail]) ->
    Elic = [{[?NOTNS|'nso-licensing-proxy'], start},
            {[?NOTNS|'unique-id'], ?CONFD_BUF(
                                      list_to_binary(
                                        "UNIQUEUUID" ++
                                            integer_to_list(N)))},
            {[?NOTNS|entitlement], start},
            {[?NOTNS|id],
             ?CONFD_BUF(<<"regid.2017-03.com.cisco.ESC,1.0_"
                          "d1445dab-9d96-4593-99f2-6f633b8a759c">>)},
            {[?NOTNS|n], ?CONFD_UINT32(N)},
            {[?NOTNS|entitlement], stop},
            {[?NOTNS|entitlement], start},
            {[?NOTNS|id],
             ?CONFD_BUF(<<"regid.2017-03.com.cisco.VM,1.0_"
                          "d1445dab-9d96-4593-99f2-6f633b8a759c">>)},
            {[?NOTNS|n], ?CONFD_UINT32(N*2)},
            {[?NOTNS|entitlement], stop},
            {[?NOTNS|'nso-licensing-proxy'], stop}],
    econfd:notification_send(Nctx, atime(), Elic);
send_notif(Nctx, [{deleted,
                   _IKP = [pong,['http://www.cisco.com/esc/esc'|esc_datamodel]]}
                  | _Tail]) ->
    Elic = [{[?NOTNS|'nso-licensing-proxy'], start},
            {[?NOTNS|'unique-id'], ?CONFD_BUF(<<"UNIQUEUUID42">>)},
            {[?NOTNS|entitlement], start},
            {[?NOTNS|id],
             ?CONFD_BUF(<<"regid.2017-03.com.cisco.ESC,1.0_"
                          "d1445dab-9d96-4593-99f2-6f633b8a759c">>)},
            {[?NOTNS|n], ?CONFD_UINT32(0)},
            {[?NOTNS|entitlement], stop},
            {[?NOTNS|entitlement], start},
            {[?NOTNS|id],
             ?CONFD_BUF(<<"regid.2017-03.com.cisco.VM,1.0_"
                          "d1445dab-9d96-4593-99f2-6f633b8a759c">>)},
            {[?NOTNS|n], ?CONFD_UINT32(0)},
            {[?NOTNS|entitlement], stop},
            {[?NOTNS|'nso-licensing-proxy'], stop}],
    econfd:notification_send(Nctx, atime(), Elic);
send_notif(Nctx, [{created, _IKP, G, DepName, SrvName, Version, Ten} | Tail]) ->
    %timer:sleep(1000),
    %timer:sleep(5000),
    VmName = mk_vm_name(Ten, DepName, G),
    Zero = 0,
    CtrBin = list_to_binary(integer_to_list(Zero)),
    DevName = <<VmName/binary, CtrBin/binary>>,
    print("here 4 ~p\n",[DevName]),
    {ok, Dir} = file:get_cwd(),
    print("I am here: ~s~n", [Dir]),
%    NSO=os:getenv("NSO"),
    NSO = Dir++"/../../..",

    if G == <<"CSR">> ->
            Ned = "cisco-ios-cli-3.8";
       G == <<"ASA">> ->
            Ned = "cisco-asa";
       G == <<"WSA">> ->
            Ned = "cisco-wsa"
    end,

    NetsimDir = NSO ++ "/netsim/" ++ binary_to_list(VmName),
    NetsimInfoDir = NSO ++ "/netsim/",
    case file:read_file_info(NetsimDir) of
        {error, _} ->

            Cmd2 = ["cp " , NetsimInfoDir ++ ".netsiminfo",
                   " ", NetsimInfoDir ++ "netsiminfo.orig"],
            print("Cmd2 is : ~p~n", [Cmd2]),
            _L1 = os:cmd(Cmd2),

            print("Adding ~p to network\n", [VmName]),
            Cmd = ["ncs-netsim add-to-network ", NSO,
                         "/packages/", Ned," 1 ", binary_to_list(VmName)],
            print("Cmd is : ~p~n", [Cmd]),
            _L0 = os:cmd(Cmd),

            %% Remove any initfiles used in ncs-netsim testing
            CDB = NetsimDir++"/"++binary_to_list(VmName)++"0/cdb/",
            case file:list_dir(CDB) of
                {ok, Files} ->
                    XmlFiles = [X || X <- Files,
                                     filename:extension(X) == ".xml",
                                     X =/= "aaa_init.xml"],
                    [file:delete(CDB++X) || X <- XmlFiles],
                    ok;
                {error, _} ->
                    ok
            end,

            os:cmd(["ncs-netsim restart ", binary_to_list(DevName)]);
        _ ->
            os:cmd(["ncs-netsim restart ", binary_to_list(DevName)])
    end,
    print("here 5\n",[]),
    %% which port did I get
    NetSimList =  os:cmd("ncs-netsim list"),
    print("Nsl = ~p~n", [NetSimList]),
    Lines = string:tokens(NetSimList, [$\n]),
    Pat=lists:flatten(io_lib:format("name=~s", [binary_to_list(DevName)])),
    print("Pat = ~p~n", [Pat]),
    [Match]=lists:zf(
              fun(Line) ->
                      Tks = string:tokens(Line, [$\s]),
                      print("Tks = ~p~n", [Tks]),
                      case lists:member(Pat, Tks) of
                          true -> {true, Line};
                          false -> false
                      end
              end, Lines),

    Toks = string:tokens(Match, [$\s, $=]),
    print("Toks = ~p~n", [Toks]),
    Port = hd(tl(lists:dropwhile(
                   fun(X) when G == <<"WSA">> ->
                           X /= "netconf";
                      (X) -> X /= "cli"
                   end,
                   Toks))),

    print("Port = ~p~n",[Port]),
    Details = Port ++ ":" ++ integer_to_list(Zero) ++
        ":" ++ Ned,


    VmUUID = uuid(),
    HostId = <<"svz-op-fdc-os-2">>,

    Exml = [{[?NOTNS|escEvent], start},
            {[?NOTNS|status], ?CONFD_ENUM_VALUE(0)},
            {[?NOTNS|svcname], ?CONFD_BUF(SrvName)},
            {[?NOTNS|svcversion], ?CONFD_BUF(Version)},
            {[?NOTNS|depname], ?CONFD_BUF(DepName)},
            {[?NOTNS|tenant], ?CONFD_BUF(Ten)},
            {[?NOTNS|vm_group], ?CONFD_BUF(G)},

            {[?NOTNS|vm_source], start},
            {[?NOTNS|vmid], ?CONFD_BUF(VmUUID)},
            {[?NOTNS|hostid], ?CONFD_BUF(HostId)},
            {[?NOTNS|vm_source], stop},


            {[?NOTNS|event], start},
            {[?NOTNS|type], ?CONFD_ENUM_VALUE(?types_VM_ALIVE)},
            {[?NOTNS|details], ?CONFD_BUF(list_to_binary(Details))},
            {[?NOTNS|event], stop},

            {[?NOTNS|escEvent], stop}],

    write_oper(Version, DepName, G, Ten, VmUUID),
    Tmp = read_tmp_file("/tmp/NO_VM_ALIVE", false),
    print("Tmp ~p~n", [Tmp]),
    DoSend = case Tmp of
                 ignore ->
                     yes;
                 [G, _,_,_] ->
                     file:delete("/tmp/NO_VM_ALIVE"),
                     no;
                 _ ->
                     yes
             end,
    if DoSend == yes ->
            case econfd:notification_send(Nctx, atime(), Exml) of
                ok ->
                    print("sent notif ~p\n",[Exml]),
                    ok;
                Error ->
                    print("Failed to send notification: ~p~n", [Error])
            end,
            if Tail == [] ->
                    send_service_alive(Nctx, SrvName, Version, DepName, Ten);
               true ->
                    send_notif(Nctx, Tail)
            end;
        true ->
            print("Dropping send of VM_ALIVE\n", [])
    end;
send_notif(Nctx, [{deleted, _IKP, G, DepName, SrvName, Version, Ten}]) ->
    %timer:sleep(1000),
    %timer:sleep(5000),
    VmName = mk_vm_name(Ten, DepName, G),
    Zero = 0,
    CtrBin = list_to_binary(integer_to_list(Zero)),
    DevName = <<VmName/binary, CtrBin/binary>>,
    print("here 4 ~p\n",[DevName]),
    {ok, Dir} = file:get_cwd(),
    print("I am here: ~s~n", [Dir]),
%    NSO=os:getenv("NSO"),
    NSO = Dir++"/../../..",

%    if G == <<"CSR">> ->
%            Ned = "cisco-ios-cli-3.8";
%       G == <<"ASA">> ->
%            Ned = "cisco-asa";
%       G == <<"WSA">> ->
%            Ned = "cisco-wsa"
%    end,

    NetsimDir = NSO ++ "/netsim/" ++ binary_to_list(VmName),
    NetsimInfoDir = NSO ++ "/netsim/",
    case file:read_file_info(NetsimDir) of
        {error, _} ->
            os:cmd(["ncs-netsim stop ", binary_to_list(DevName)]);
        _ ->
            os:cmd(["ncs-netsim stop ", binary_to_list(DevName)]),

            print("removing ~p from network\n", [VmName]),
            Cmd = ["rm -rf " , NetsimDir],
            print("Cmd is : ~p~n", [Cmd]),
            _L0 = os:cmd(Cmd),

            Cmd2 = ["cp " , NetsimInfoDir ++ "netsiminfo.orig",
                   " ", NetsimInfoDir ++ ".netsiminfo"],
            print("Cmd2 is : ~p~n", [Cmd2]),
            _L1 = os:cmd(Cmd2)

    end,
    print("here 5\n",[]),
    %% which port did I get
    Details = "0:0",


    VmUUID = uuid(),
    HostId = <<"svz-op-fdc-os-2">>,

    Exml = [{[?NOTNS|escEvent], start},
            {[?NOTNS|status], ?CONFD_ENUM_VALUE(0)},
            {[?NOTNS|svcname], ?CONFD_BUF(SrvName)},
            {[?NOTNS|svcversion], ?CONFD_BUF(Version)},
            {[?NOTNS|depname], ?CONFD_BUF(DepName)},
            {[?NOTNS|tenant], ?CONFD_BUF(Ten)},
            {[?NOTNS|vm_group], ?CONFD_BUF(G)},

            {[?NOTNS|vm_source], start},
            {[?NOTNS|vmid], ?CONFD_BUF(VmUUID)},
            {[?NOTNS|hostid], ?CONFD_BUF(HostId)},
            {[?NOTNS|vm_source], stop},


            {[?NOTNS|event], start},
            {[?NOTNS|type], ?CONFD_ENUM_VALUE(?types_VM_UNDEPLOYED)},
            {[?NOTNS|details], ?CONFD_BUF(list_to_binary(Details))},
            {[?NOTNS|event], stop},

            {[?NOTNS|escEvent], stop}],

    case econfd:notification_send(Nctx, atime(), Exml) of
        ok ->
            print("sent notif ~p\n",[Exml]),
            ok;
        Error ->
            print("Failed to send notification: ~p~n", [Error])
    end.

send_service_alive(Nctx, SrvName, Version, DepName, Ten) ->
    %timer:sleep(1000),
    Exml = [{[?NOTNS|escEvent], start},
            {[?NOTNS|status], ?CONFD_ENUM_VALUE(0)},
            {[?NOTNS|svcname], ?CONFD_BUF(SrvName)},
            {[?NOTNS|svcversion], ?CONFD_BUF(Version)},
            {[?NOTNS|depname], ?CONFD_BUF(DepName)},
            {[?NOTNS|tenant], ?CONFD_BUF(Ten)},


            {[?NOTNS|event], start},
            {[?NOTNS|type], ?CONFD_ENUM_VALUE(?types_SERVICE_ALIVE)},
            {[?NOTNS|event], stop},

            {[?NOTNS|escEvent], stop}],

    case econfd:notification_send(Nctx, atime(), Exml) of
        ok ->
            print("sent srv alive notif ~p\n",[Exml]),
            ok;
        Error ->
            print("Failed to send srv alive notification: ~p~n", [Error])
    end.


read_tmp_file(F, DoDel) ->
    %print("Try ~p~n", [F]),
    case file:read_file(F) of
        {ok, Bin} ->
            print("Found ~p for ~p~n", [Bin, F]),
            Ret = case string:tokens(binary_to_list(Bin), " \n") of
                      LL = [_Gr0, _ServiceName0, _DepName0, _Tenant0] ->
                          [ list_to_binary(X) || X <- LL];
                      _ ->
                          print("Bad /tmp data ~p", [Bin]),
                          ignore
            end,
            if DoDel ->
                    file:delete(F);
               true ->
                    ok
            end,
            Ret;
        _ ->
            ignore
    end.


tmp_kicker(Nctx) ->
    try
        tmp_kicker2(Nctx)
    catch
        X:Y:Stacktrace ->
        print("ERR ~p~n~p~n", [{X,Y}, Stacktrace])
    end,
    tmp_kicker(Nctx).

tmp_kicker2(Nctx) ->
        %timer:sleep(1000),
        tmp_kicker2(Nctx, "VM_RECOVERY_COMPLETE"),
        tmp_kicker2(Nctx, "VM_RECOVERY_COMPLETE_FAILURE"),
        tmp_kicker2(Nctx, "VM_RECOVERY_UNDEPLOYED"),
        tmp_kicker2(Nctx, "SERVICE_ALIVE_FAILURE").


tmp_kicker2(Nctx, F0) ->
    F = "/tmp/" ++ F0,
    case read_tmp_file(F, true) of
        ignore -> ignore;
        LL = [_Gr, _SrvName, _DepName, _Tenant] ->
            if F0 == "VM_RECOVERY_COMPLETE" ->
                    vm_recover_notif(Nctx, LL);
               F0 == "VM_RECOVERY_COMPLETE_FAILURE" ->
                    vm_recover_notif_fail(Nctx, LL);
               F0 == "VM_RECOVERY_UNDEPLOYED" ->
                    vm_recover_undep(Nctx, LL);
               F0 == "SERVICE_ALIVE_FAILURE" ->
                    serv_alive_fail(Nctx, LL);
               true ->
                    ok
            end
    end.


serv_alive_fail(Nctx, Args = [Gr, SrvName, DepName, Tenant] ) ->
    Details = details(Args),

    Msg = <<"Ho ho !! srv alive fauilure">>,
    Exml = [{[?NOTNS|escEvent], start},
            {[?NOTNS|status], ?CONFD_ENUM_VALUE(1)},
            {[?NOTNS|status_message], ?CONFD_BUF(Msg)},
            {[?NOTNS|svcname], ?CONFD_BUF(SrvName)},
            {[?NOTNS|svcversion], ?CONFD_BUF(<<"1.1">>)},
            {[?NOTNS|depname], ?CONFD_BUF(DepName)},
            {[?NOTNS|tenant], ?CONFD_BUF(Tenant)},
            {[?NOTNS|vm_group], ?CONFD_BUF(Gr)},

            {[?NOTNS|event], start},
            {[?NOTNS|type], ?CONFD_ENUM_VALUE(?types_SERVICE_ALIVE)},
            {[?NOTNS|details], ?CONFD_BUF(list_to_binary(Details))},
            {[?NOTNS|event], stop},

            {[?NOTNS|escEvent], stop}],

    case econfd:notification_send(Nctx, atime(), Exml) of
        ok ->
            print("sent recover notif \n",[]),
            ok;
        Error ->
            print("Failed to send recover notification: ~p~n", [Error])
    end.


vm_recover_undep(Nctx, Args = [Gr, SrvName, DepName, Tenant] ) ->
    Details = details(Args),

    Msg = <<"Ho ho !! ">>,
    Exml = [{[?NOTNS|escEvent], start},
            {[?NOTNS|status], ?CONFD_ENUM_VALUE(0)},
            {[?NOTNS|status_message], ?CONFD_BUF(Msg)},
            {[?NOTNS|svcname], ?CONFD_BUF(SrvName)},
            {[?NOTNS|svcversion], ?CONFD_BUF(<<"1.1">>)},
            {[?NOTNS|depname], ?CONFD_BUF(DepName)},
            {[?NOTNS|tenant], ?CONFD_BUF(Tenant)},
            {[?NOTNS|vm_group], ?CONFD_BUF(Gr)},

            {[?NOTNS|event], start},
            {[?NOTNS|type], ?CONFD_ENUM_VALUE(?types_VM_RECOVERY_UNDEPLOYED)},
            {[?NOTNS|details], ?CONFD_BUF(list_to_binary(Details))},
            {[?NOTNS|event], stop},

            {[?NOTNS|escEvent], stop}],

    case econfd:notification_send(Nctx, atime(), Exml) of
        ok ->
            print("sent recover notif \n",[]),
            ok;
        Error ->
            print("Failed to send recover notification: ~p~n", [Error])
    end.


details([Gr, _SrvName, DepName, Tenant] ) ->
    VmName = mk_vm_name(Tenant, DepName, Gr),
    Zero = 0,
    CtrBin = list_to_binary(integer_to_list(Zero)),
    DevName = <<VmName/binary, CtrBin/binary>>,


    if Gr == <<"CSR">> ->
            Ned = "cisco-ios-cli-3.8";
       Gr == <<"ASA">> ->
            Ned = "cisco-asa";
       Gr == <<"WSA">> ->
            Ned = "cisco-wsa"
    end,


    NetSimList =  os:cmd("ncs-netsim list"),
    print("Nsl = ~p~n", [NetSimList]),
    Lines = string:tokens(NetSimList, [$\n]),
    Pat=lists:flatten(io_lib:format("name=~s", [binary_to_list(DevName)])),
    print("Pat = ~p~n", [Pat]),
    [Match]=lists:zf(
              fun(Line) ->
                      Tks = string:tokens(Line, [$\s]),
                      print("Tks = ~p~n", [Tks]),
                      case lists:member(Pat, Tks) of
                          true -> {true, Line};
                          false -> false
                      end
              end, Lines),

    Toks = string:tokens(Match, [$\s, $=]),
    print("Toks = ~p~n", [Toks]),
    Port = hd(tl(lists:dropwhile(
                   fun(X) when Gr == <<"WSA">> ->
                           X /= "netconf";
                      (X) -> X /= "cli"
                   end,
                   Toks))),

    print("Port = ~p~n",[Port]),
    _Details = Port ++ ":" ++ integer_to_list(Zero) ++
        ":" ++ Ned.


vm_recover_notif(Nctx, Args = [Gr, SrvName, DepName, Tenant] ) ->

    Details = details(Args),
    VmUUID = uuid(),
    HostId = <<"svz-op-fdc-os-2">>,
    Msg = <<"Ho ho !! ">>,
    Exml = [{[?NOTNS|escEvent], start},
            {[?NOTNS|status], ?CONFD_ENUM_VALUE(0)},
            {[?NOTNS|status_message], ?CONFD_BUF(Msg)},
            {[?NOTNS|svcname], ?CONFD_BUF(SrvName)},
            {[?NOTNS|svcversion], ?CONFD_BUF(<<"1.1">>)},
            {[?NOTNS|depname], ?CONFD_BUF(DepName)},
            {[?NOTNS|tenant], ?CONFD_BUF(Tenant)},
            {[?NOTNS|vm_group], ?CONFD_BUF(Gr)},

            {[?NOTNS|vm_target], start},
            {[?NOTNS|vmid], ?CONFD_BUF(VmUUID)},
            {[?NOTNS|hostid], ?CONFD_BUF(HostId)},
            {[?NOTNS|vm_target], stop},


            {[?NOTNS|event], start},
            {[?NOTNS|type], ?CONFD_ENUM_VALUE(?types_VM_RECOVERY_COMPLETE)},
            {[?NOTNS|details], ?CONFD_BUF(list_to_binary(Details))},
            {[?NOTNS|event], stop},


            {[?NOTNS|escEvent], stop}],

    case econfd:notification_send(Nctx, atime(), Exml) of
        ok ->
            print("sent recover notif \n",[]),
            ok;
        Error ->
            print("Failed to send recover notification: ~p~n", [Error])
    end.


vm_recover_notif_fail(Nctx, Args = [Gr, SrvName, DepName, Tenant] ) ->
    Msg = <<" recovery FAILING ">>,
    Details = details(Args),
    Exml = [{[?NOTNS|escEvent], start},
            {[?NOTNS|status], ?CONFD_ENUM_VALUE(1)},
            {[?NOTNS|status_message], ?CONFD_BUF(Msg)},
            {[?NOTNS|svcname], ?CONFD_BUF(SrvName)},
            {[?NOTNS|svcversion], ?CONFD_BUF(<<"1.1">>)},
            {[?NOTNS|depname], ?CONFD_BUF(DepName)},
            {[?NOTNS|tenant], ?CONFD_BUF(Tenant)},
            {[?NOTNS|vm_group], ?CONFD_BUF(Gr)},

            {[?NOTNS|event], start},
            {[?NOTNS|type], ?CONFD_ENUM_VALUE(?types_VM_RECOVERY_COMPLETE)},
            {[?NOTNS|details], ?CONFD_BUF(list_to_binary(Details))},
            {[?NOTNS|event], stop},


            {[?NOTNS|escEvent], stop}],

    case econfd:notification_send(Nctx, atime(), Exml) of
        ok ->
            print("sent recover notif \n",[]),
            ok;
        Error ->
            print("Failed to send recover fail notification: ~p~n", [Error])
    end.


now_to_dateTime(Now) ->
    now_to_dateTime(Now, true).
now_to_dateTime(Now, WithMicro) ->
    case WithMicro of
        true ->
            {_, _, Micro} = Now;
        false ->
            Micro = 0
    end,
    {{Y,Mo,D},{H,Mi,S}} = calendar:now_to_local_time(Now),
    case calendar:now_to_universal_time(Now) of
        {{Y,Mo,D},{UH,UMi,S}} ->
            Tz = H - UH,
            Tzm = Mi - UMi;
        _ ->
            %% timezone info isn't known
            Tz = [],
            Tzm = 0
    end,
    {?C_DATETIME, {Y,Mo,D,H,Mi,S,Micro,Tz,Tzm}}.

atime() ->
    now_to_dateTime(erlang:timestamp()).

printer() ->
    case whereis(printer) of
        undefined ->
            register(printer, self()),
            {ok, Fd} = file:open("./logs/escnotif.log", [write]),
            ploop(Fd);
        _ ->
            ok
    end.

ploop(Fd) ->
    receive
        {Fmt, Args} ->
            io:format(Fd, Fmt, Args),
            ploop(Fd)
    end.

print(Fmt, Args) ->
    printer ! {Fmt, Args}.



uuid() ->
    list_to_binary(
      [uuid(8) ++ "-" ++ uuid(4) ++ "-" ++ uuid(4) ++ "-" ++
           uuid(4) ++ "-" ++ uuid(12)]).

uuid(0) ->
    [];
uuid(I) ->
    [hd(integer_to_list(rand:uniform(9))) | uuid(I-1)].



write_oper(Version, DepName, G, Ten, VmUUID) ->
    VmName = mk_vm_name(Ten, DepName, G),
    load_oper(
      [{"TENANT",  binary_to_list(Ten)},
       {"DEPNAME", binary_to_list(DepName)},
       {"VERSION", binary_to_list(Version)},
       {"VMGROUP", binary_to_list(G)},
       {"VMUUID",  binary_to_list(VmUUID)},
       {"VMNAME",  binary_to_list(VmName)}]).


load_oper(Vars) ->
    {ok,B} = file:read_file("oper_template.xml"),
    S = subst(Vars, binary_to_list(B)),
    file:write_file("oper_subst.xml", S),
    Res = os:cmd("confd_load -l -m -O oper_subst.xml"),
    case Res of
        [] ->
            ok;
        _ ->
            print("Failed to load: ~s", [Res])
    end.

subst([], S) ->
    S;
subst([{Var, Val} | Vars], S) ->
    S2 = subst(Var, Val, S),
    subst(Vars, S2).

subst(Var, Val, [$$ | S]) ->
    case prefix(Var, S) of
        {true, Rest} ->
            [Val | subst(Var, Val, Rest)];
        false ->
            [$$ | subst(Var, Val, S)]
    end;
subst(Var, Val, [H|T]) ->
    [H | subst(Var, Val, T)];
subst(_,_,[]) ->
    [].

prefix([], Rest) ->
    {true, Rest};
prefix([H|T1], [H|T2]) ->
    prefix(T1, T2);
prefix(_, _) ->
    false.
