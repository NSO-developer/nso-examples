-module(if_complete).

-include("econfd.hrl").
-include("econfd_errors.hrl").

-on_load(on_load/0).

on_load() ->
    proc_lib:spawn(fun start/0),
    ok.

start() ->
    %% for supervision
    process_flag(trap_exit, true),
    start(0).

start(Restarts) ->
    {ok, MaapiSock} = econfd_maapi:connect({127,0,0,1}, ?NCS_PORT),
    Action = #confd_action_cb{actionpoint = 'if-complete',
                              completion = fun complete/9
                             },


    {ok,Daemon} = econfd:init_daemon(if_complete, ?CONFD_SILENT,user, MaapiSock,
                                    {127,0,0,1}, ?NCS_PORT),
    ok = econfd:register_action_cb(Daemon, Action),
    ok = econfd:register_done(Daemon),
    supervise(Restarts, MaapiSock, Daemon).


%% simple supervision
supervise(Restarts, MaapiSock, Daemon) ->
    receive
        {'EXIT', Daemon, _} when Restarts < 3 ->
            econfd_maapi:close(MaapiSock),
            start(Restarts + 1);
        {'EXIT', Daemon, _} ->
            exit(too_many_restarts)
    after
        timeout(Restarts) ->
            supervise(0, MaapiSock, Daemon)
    end.

timeout(0)         -> infinity;
timeout(_Restarts) -> 10000.


-define(NS, 'http://tail-f.com/ns/ncs').
-define(ios_urn, 'urn:ios').
-define(nexus_urn, 'http://tail-f.com/ned/cisco-nx').
-define(iosxr_urn, 'http://tail-f.com/ned/cisco-ios-xr').
-define(iosxe_urn, 'http://tail-f.com/ned/cisco-ios-xe').
-define(force10_urn, 'http://tail-f.com/ned/dell-ftos').

complete(UInfo, _CliStyle, _Token, _CompletionChar, _IKP,
         CmdPath, _Id, _Tp, _Extra)->
    Ax = UInfo#confd_user_info.actx,
    Dx = Ax#confd_action_ctx.dx,
    Maapi = Dx#confd_daemon_ctx.d_opaque,
    Th = Ax#confd_action_ctx.thandle,
    econfd_maapi:attach2(Maapi, ?NS, UInfo#confd_user_info.usid, Th),
    %% We're completing the endpoints, the device has already been given
    Dev = list_to_binary(lists:last(
                           string:tokens(
                             binary_to_list(CmdPath), " "))),

    %% what kind of Dev is this,
    DevPath = [{Dev}, device, [?NS|devices]],
    ModKeys = econfd_maapi:all_keys(Maapi, Th, [module | DevPath]),
    DevType = dev_type(ModKeys),
    collect(Maapi, Th, DevType, iface_paths(DevType, DevPath), []).

%% Given the module, which different lists can we find
%% under the interface container

iface_types(<<"tailf-ned-cisco-ios">>) ->
    ['Loopback', 'GigabitEthernet', 'Port-channel',
     'FastEthernet', 'TenGigabitEthernet'];
iface_types(<<"tailf-ned-cisco-nx">>) ->
    ['Ethernet', fc, 'port-channel', 'san-port-channel'];
iface_types(<<"tailf-ned-cisco-ios-xr">>) ->
    ['Loopback', 'MgmtEth', 'TenGigE', 'GigabitEthernet'];
iface_types(<<"tailf-ned-cisco-ios-xe">>) ->
    ['GigabitEthernet', 'Loopback', 'Port-channel', 'TenGigabitEthernet',
     'FastEthernet'];
iface_types(<<"tailf-ned-dell-ftos">>) ->
    ['fortyGigE', 'ManagementEthernet', 'GigabitEthernet',
     'TenGigabitEthernet'].


%% Given the module, what is the namespace
urn(<<"tailf-ned-cisco-ios">>) ->
    ?ios_urn;
urn(<<"tailf-ned-cisco-nx">>) ->
    ?nexus_urn;
urn(<<"tailf-ned-cisco-ios-xr">>) ->
    ?iosxr_urn;
urn(<<"tailf-ned-cisco-ios-xe">>) ->
    ?iosxe_urn;
urn(<<"tailf-ned-dell-ftos">>) ->
    ?force10_urn;
urn(<<"junos">>) ->
    'http://xml.juniper.net/xnm/1.1/xnm'.

iface_paths(unknown, _) ->
    [];
iface_paths(<<"junos">>, DevPath) ->
    %% what about units
    [[interface, interfaces,
      ['http://xml.juniper.net/xnm/1.1/xnm'|configuration],
      config |DevPath]];
iface_paths(DT, DevPath) ->
    Types = iface_types(DT),
    lists:map(fun(Type) ->
                      [Type , [urn(DT)|interface], config | DevPath]
              end, Types).

collect(Maapi, Th, <<"junos">>, [ Path | Tail], Acc) ->
     case econfd_maapi:all_keys(Maapi, Th, Path) of
        {ok, Keys} ->
             L = lists:map(
                   fun({K}) -> lists:flatten(
                                 io_lib:format("~s",[k2s(K)]))
                   end, Keys),
             collect(Maapi, Th, <<"junos">>, Tail, L ++ Acc);
        Err ->
            Err
    end;
collect(Maapi, Th, DevType, [ Path | Tail], Acc) ->
    Type = hd(Path),
    case econfd_maapi:all_keys(Maapi, Th, Path) of
        {ok, Keys} ->
            L = lists:map(
                  fun({K}) ->
                          lists:flatten(
                            io_lib:format("~s~s",[Type, k2s(K)]))
                  end, Keys),
            collect(Maapi, Th, DevType, Tail, L ++ Acc);
        Err ->
            Err
    end;
collect(_,_, _,[], Acc) ->
    {ok, Acc}.

k2s({_, Int}) ->
    k2s(Int);
k2s(I) when is_integer(I) ->
    integer_to_list(I);
k2s(X) when is_binary(X) ->
    binary_to_list(X).


dev_type({ok, Keys}) ->
    dev_type([<<"tailf-ned-cisco-ios">>, <<"tailf-ned-cisco-nx">>,
              <<"tailf-ned-cisco-ios-xr">>,
              <<"tailf-ned-dell-ftos">>], Keys).
dev_type([H|T], Keys) ->
    case lists:member({H}, Keys) of
        true ->
            H;
        false ->
            dev_type(T, Keys)
    end;
dev_type([], _) ->
    unknown.



