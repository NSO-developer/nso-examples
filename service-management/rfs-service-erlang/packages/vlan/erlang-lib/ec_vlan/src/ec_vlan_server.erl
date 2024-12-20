-module(ec_vlan_server).

-behaviour(gen_server).

%% API
-export([
         start_link/0
         %% ping/0
        ]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2,
         terminate/2, code_change/3]).

-include_lib("econfd/include/econfd.hrl").
-include_lib("econfd/include/econfd_errors.hrl").

-define(SERVER, ?MODULE).
-define(maapi_socket(TransCtx),
        TransCtx#confd_trans_ctx.opaque).
-define(service_ctx(TransCtx),
        (TransCtx#confd_trans_ctx.dx)#confd_daemon_ctx.d_opaque).
-define(usid(TransCtx),
        (TransCtx#confd_trans_ctx.uinfo)#confd_user_info.usid).
-define(check(Res),
        fun(Error) when element(1, Error) =:= error ->
                throw(Error);
           (Ok) ->
                Ok
        end(Res)).

-record(state, {daemon}).

-record(service_ctx, {servicepoint}).

%%%===================================================================
%%% API
%%%===================================================================

start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

%% ping() ->
%%     call(ping).
%%
%% call(Msg) ->
%%     gen_server:call(?SERVER, Msg, infinity).

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

%%--------------------------------------------------------------------
init([]) ->
    process_flag(trap_exit, true), % Triggers call to terminate/2
    DebugLevel = ?CONFD_SILENT,   % ?CONFD_TRACE
    State = register_callbacks(DebugLevel),
    log(info, "Server started", []),
    {ok, State}.

%%--------------------------------------------------------------------
%% handle_call(ping, _From, State) ->
%%     Reply = pong,
%%     {reply, Reply, State};
handle_call(Req, _From, State) ->
    log(error, "Got unexpected call: ~p", [Req]),
    Reply = error,
    {reply, Reply, State}.

%%--------------------------------------------------------------------
handle_cast(Msg, State) ->
    log(error, "Got unexpected cast: ~p", [Msg]),
    {noreply, State}.

%%--------------------------------------------------------------------
handle_info(Info, State) ->
    log(error, "Got unexpected info: ~p", [Info]),
    {noreply, State}.

%%--------------------------------------------------------------------
terminate(Reason, _State) ->
    log(info, "Server stopped - ~p", [Reason]),
    ok.

%%--------------------------------------------------------------------
code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%%===================================================================
%%% Internal functions
%%%===================================================================

register_callbacks(DebugLevel) ->
    TransCbs = #confd_trans_cbs{init   = fun trans_init/1,
                                abort  = fun trans_abort/1,
                                finish = fun trans_finish/1},
    ServicePoint = vlanspnt,
    ServiceCbs = #ncs_service_cbs{create = fun create/4,
                                  servicepoint = ServicePoint},
    Host = {127,0,0,1},
    Port = ?NCS_PORT,
    ServiceCtx = #service_ctx{servicepoint=ServicePoint},
    DaemonName = list_to_atom(lists:concat([?MODULE, "_", ServicePoint])),
    {ok, Daemon} = econfd:init_daemon(DaemonName, DebugLevel, user,
                                      ServiceCtx, Host, Port),
    ok = econfd:register_trans_cb(Daemon, TransCbs),
    ok = econfd:register_service_cb(Daemon,ServiceCbs),
    ok = econfd:register_done(Daemon),

    #state{daemon=Daemon}.

trans_init(TransCtx) ->
    %% Use a separate maapi socket for each transaction
    {ok, MaapiSock} = econfd_maapi:connect({127,0,0,1}, ?NCS_PORT),
    {ok, TransCtx#confd_trans_ctx{opaque = MaapiSock}}.

trans_abort(TransCtx) ->
    log(trace, "=== Abort invoked ===", []),
    {ok, TransCtx}.

trans_finish(TransCtx) ->
    M = ?maapi_socket(TransCtx),
    econfd_maapi:close(M),
    ok.

create(TransCtx, ServiceIKP, PropList, Tid) ->
    log(trace, "create service ~p", [ServiceIKP]),
    try
        attach_trans(TransCtx, Tid),
        check_devices(TransCtx, Tid),
        update_devices(TransCtx, Tid, ServiceIKP),
        {ok, PropList}
    catch
        throw:{error, Reason} ->
            Msg = cli_write(TransCtx, Reason),
            {error, #confd_error{code=application_internal, str=Msg}};
        throw:{error, Format, Args} ->
            Msg = cli_write(TransCtx, Format, Args),
            {error, #confd_error{code=application_internal, str=Msg}};
        Class:Reason ->
            Msg = cli_write(TransCtx, "Service creation failure: ~p ~p",
                            [Class, Reason]),
            {error, #confd_error{code=application_internal, str=Msg}}
    after
            detach_trans(TransCtx, Tid)
    end.

check_devices(TransCtx, Tid) ->
    %% check if it is reasonable to assume that devices
    %% initially has been sync-from:ed
    M = ?maapi_socket(TransCtx),
    ManagedDevicesIKP = [device,['http://tail-f.com/ns/ncs'|devices]],
    HasCapabilities =
        fun(DevKey) ->
                CIKP = [capabilities,DevKey|ManagedDevicesIKP],
                econfd_maapi:num_instances(M, Tid, CIKP) =/= 0
        end,
    iterate(M, Tid, ManagedDevicesIKP, ok,
            <<"Not able to check devices">>,
            fun(DevKey, Acc) ->
                    case HasCapabilities(DevKey) of
                        true ->
                            {continue, Acc};
                        false ->
                            throw({error,
                                   "Device ~s has no known capabilities"
                                   ", has sync-from been performed?",
                                   [DevKey]})
                    end
            end).

update_devices(TransCtx, Tid, ServiceIKP) ->
    %% check if it is reasonable to assume that devices
    %% initially has been sync-from:ed

    M = ?maapi_socket(TransCtx),
    ManagedDevicesIKP = [device,['http://tail-f.com/ns/ncs'|devices]],
    iterate(M, Tid, ManagedDevicesIKP, ok,
            <<"Could not instantiate service">>,
            fun(DevKey, Acc) ->
                    %% execute as shared create of the path
                    %% /interfaces/interface[name='x']/unit[name='i']
                    IfListIKP = [interface,interfaces,
                                 ['http://example.com/router'|sys],
                                 config,DevKey|ManagedDevicesIKP],
                    Iface = ?check(get_elem(M, Tid, [iface|ServiceIKP])),
                    IfIKP = [{Iface}|IfListIKP],
                    ?check(econfd_maapi:shared_create(M, Tid, IfIKP)),
                    ?check(econfd_maapi:shared_create(M, Tid,
                                                      [enabled|IfIKP])),
                    Unit = ?check(get_elem(M, Tid, [unit|ServiceIKP])),
                    UnitIKP = [{Unit},unit|IfIKP],
                    ?check(econfd_maapi:shared_create(M, Tid, UnitIKP)),
                    Vid = ?check(get_elem(M, Tid, [vid|ServiceIKP])),
                    ?check(econfd_maapi:shared_set_elem(M, Tid,
                                                        ['vlan-id'|UnitIKP],
                                                        Vid)),
                    ?check(econfd_maapi:shared_set_elem(M, Tid,
                                                        [enabled|UnitIKP],
                                                        true)),
                    Desc = ?check(get_elem(M, Tid, [description|ServiceIKP])),
                    ?check(econfd_maapi:shared_set_elem(M, Tid,
                                                        [description|UnitIKP],
                                                        Desc)),
                    ?check(iterate(M, Tid, [arp|ServiceIKP], ok,
                                   <<"Error while processing arp settings">>,
                                   fun(ArpKey, ok) ->
                                           ArpEntry = [ArpKey, arp|UnitIKP],
                                           ?check(econfd_maapi:shared_create(
                                                    M, Tid, ArpEntry)),
                                           {continue, ok}
                                   end)),
                    {continue, Acc}
            end).

%%%===================================================================
%%% Goodies
%%%===================================================================

get_elem(M, Tid, IKP) ->
    {ok, Val} = ?check(econfd_maapi:get_elem(M, Tid, IKP)),
    Val.

attach_trans(TransCtx, Tid) ->
    M = ?maapi_socket(TransCtx),
    Usid = ?usid(TransCtx),
    ?check(econfd_maapi:attach2(M, 0, Usid, Tid)).

detach_trans(TransCtx, Tid) ->
    M = ?maapi_socket(TransCtx),
    econfd_maapi:detach(M, Tid).

iterate(M, Tid, IKP, Acc, ErrPrefix, Fun) when is_function(Fun, 2) ->
    C = econfd_maapi:init_cursor(M, Tid, IKP),
    do_iterate(C, Acc, ErrPrefix, Fun).

do_iterate(Cursor, Acc, ErrPrefix, Fun) ->
    case econfd_maapi:get_next(Cursor) of
        {ok, Key, NewCursor} ->
            case Fun(Key, Acc) of
                {continue, NewAcc} ->
                    do_iterate(NewCursor, NewAcc, ErrPrefix, Fun);
                {return, NewAcc} ->
                    {ok, NewAcc}
            end;
        done ->
            Acc;
        {error, closed} ->
            Code = ?CONFD_ERR_APPLICATION_INTERNAL,
            Reason = <<"Socket closed">>,
            throw({error, {Code,<<ErrPrefix/binary,Reason/binary>>}});
        {error, {Code,Reason}} ->
            throw({error, {Code,<<ErrPrefix/binary,Reason/binary>>}})
    end.

cli_write(TransCtx, Format, Args) ->
    Msg = iolist_to_binary(io_lib:format(Format, Args)),
    cli_write(TransCtx, Msg).

cli_write(TransCtx, closed) ->
    cli_write(TransCtx, <<"Socket closed">>);
cli_write(TransCtx, {Code, Msg}) when is_integer(Code) ->
    cli_write(TransCtx, Msg);
cli_write(_TransCtx, Msg) when is_binary(Msg) ->
    error_logger:error_msg("~s\n", [Msg]),
%%     Usid = ?usid(TransCtx),
%%     M = ?maapi_socket(TransCtx),
%%     ok = econfd_maapi:cli_write(M, Usid, Msg),
    Msg.

log(error, Format, Args) ->
    econfd:log(?CONFD_LEVEL_ERROR, "~p: " ++ Format, [?SERVER|Args]);
log(info, Format, Args) ->
    econfd:log(?CONFD_LEVEL_INFO,  "~p: " ++ Format, [?SERVER|Args]);
log(trace, Format, Args) ->
    econfd:log(?CONFD_LEVEL_TRACE, "~p: " ++ Format, [?SERVER|Args]).
