%%%-------------------------------------------------------------------
%%% @doc Cisco Inventory Updater server
%%%-------------------------------------------------------------------
-module(ec_inventory_updater_server).

-behaviour(gen_server).

%% External exports
-export([start_link/0,
         stop/0,
         config_changed/4
        ]).

%% gen_server callbacks
-export([init/1,
         handle_call/3,
         handle_cast/2,
         handle_info/2,
         terminate/2,
         code_change/3,
         register_kickers/0]).

-include("ec_inventory_updater.hrl").
-include("econfd.hrl").
-include("econfd_errors.hrl").

-define(SERVER, ?MODULE).
-define(NCSNS, 'http://tail-f.com/ns/ncs').
-define(DRFSNS, 'http://com/tail-f/drfs').

-record(state, {
         }).

%% %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% %%


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% Gen server


%%%----------------------------------------------------------------------
%%% API
%%%----------------------------------------------------------------------
start_link() ->
    application:start(econfd),
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

stop() ->
    gen_server:call(?SERVER, stop, infinity).


%%%----------------------------------------------------------------------
%%% Callback functions from gen_server
%%%----------------------------------------------------------------------

%%----------------------------------------------------------------------
%% Func: init/1
%% Returns: {ok, State}          |
%%          {ok, State, Timeout} |
%%          ignore               |
%%          {stop, Reason}
%%----------------------------------------------------------------------
init([]) ->
    case econfd_maapi:connect({127,0,0,1}, ?NCS_PORT) of
        {ok, M} ->
            {ok, D} = econfd:init_daemon('inventory-updater',
                                         ?CONFD_DEBUG, user, M,
                                         {127,0,0,1}, ?NCS_PORT),
            econfd:controlling_process(M,D),
            econfd:register_done(D),
            proc_lib:spawn(fun() ->
                                   register_kickers()
                           end),
            State0 = #state{},
            {ok, State0};
        _Err ->
            {stop, error_connecting_maapi}
    end.

%%----------------------------------------------------------------------
%% Func: handle_call/3
%% Returns: {reply, Reply, State}          |
%%          {reply, Reply, State, Timeout} |
%%          {noreply, State}               |
%%          {noreply, State, Timeout}      |
%%          {stop, Reason, Reply, State}   | (terminate/2 is called)
%%          {stop, Reason, State}            (terminate/2 is called)
%%----------------------------------------------------------------------
handle_call(stop, _From, S) ->
    {stop, normal, S};
%%
%%

handle_call(register_kickers, _From, S) ->
    DIKP = ['device', [?NCSNS | 'devices']],
    Monitor = kicker_server:add_wildcard(DIKP),
    kicker_server:register_data_kicker(
      Monitor, undefined, undefined,
      {?MODULE, config_changed, []}),

    DrfsIKP = ['device', [?DRFSNS | 'dRFS']],
    DrfsMonitor = kicker_server:add_wildcard(DrfsIKP),
    kicker_server:register_data_kicker(
      DrfsMonitor, undefined, undefined,
      {?MODULE, config_changed, []}),

    {reply, ok, S};
handle_call(_Request, _From, S) ->
    Res = {struct, [{status, "error"},
                    {reason, "unknown request"}]},
    {reply, Res, S}.

%%----------------------------------------------------------------------
%% Func: handle_cast/2
%% Returns: {noreply, State}          |
%%          {noreply, State, Timeout} |
%%          {stop, Reason, State}            (terminate/2 is called)
%%----------------------------------------------------------------------
handle_cast(_Msg, State) ->
    {noreply, State}.

%%----------------------------------------------------------------------
%% Func: handle_info/2
%% Returns: {noreply, State}          |
%%          {noreply, State, Timeout} |
%%          {stop, Reason, State}            (terminate/2 is called)
%%----------------------------------------------------------------------
handle_info(_Info, State) ->
    {noreply, State}.

%%----------------------------------------------------------------------
%% Func: terminate/2
%% Purpose: Shutdown the server
%% Returns: any (ignored by gen_server)
%%----------------------------------------------------------------------
terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, S, _Extra) ->
    {ok, S}.

%%%----------------------------------------------------------------------
%%% Internal functions
%%%----------------------------------------------------------------------
register_kickers() ->
    confd_server:wait_started(),
    gen_server:call(?SERVER, register_kickers).

config_changed(prepare, {OldTh, NewTh}, IKeypath, _A) ->
    [{DevName}|_] = IKeypath,
    USid = cs_trans:get_usid(OldTh),
    %% {ok, Str} = ncs_util:xml_diff_to_string(OldTh, NewTh, AState, IKeypath),
    FileName = "../db_store/"++?b2l(DevName)++".cfg",
    case {file:read_file_info(FileName),
          cs_trans:exists(OldTh, IKeypath) == true} of
        {{ok, _FileInfo}, true} ->
            {ok, Str} = get_j_delta(NewTh, OldTh, USid, [IKeypath]),
            Cfg = {delta, Str};
        _OtherwiseFull ->
            RfsIKP = [{DevName},device,[?DRFSNS|dRFS]],
            DevIKP = [{DevName},device,[?NCSNS|devices]],
            {ok, Str} = get_j_config(NewTh, USid, [DevIKP,RfsIKP]),
            Cfg = {full, Str}
    end,
    {true, {DevName, Cfg}};
config_changed(commit, _ChgXds, _IKeypath, {DevName, Cfg}) ->
    save_config(DevName, Cfg),
    ok.

get_j_config(Th, USid, IKPs) ->
    IoHandler = spawn_link(fun() -> iohandler() end),
    [cli_show:show(
       IoHandler,
       Th,
       fxs_server:get_ns_list(),
       _WithDefaults=false,
       _ShowDefaults=false,
       _EmitParents=true,
       IKeypath,
       _Filters=[],
       _DeSelect=[],
       _Mode=cdb,
       _Visible=['*', full],
       _SortBys=[],
       _MaxDepth=999999999,
       _ExportedNS0=no_export_none,
       _ShowTable=false,
       _CliState=undefined,
       _DisplayGroups=[],
       _EditPath=[],
       _ShowSet0=false,
       _EnforceTable=false,
       _ShowTags=true,
       _ShowAnnotations=true,
       _TagsFilter=[],
       _AnnotationsFilter=[],
       _Extend=false,
       _DisplayEmptyConfigFun=undefined,
       _FilterOp=default,
       _NoAltName=true,
       _ShowEnable=true,
       _ShowNS=true,
       _NoDropElem=true,
       _NoExposeName=true,
       _ShowAttrs=true,
       _ShowServiceMeta=true,
       _IsRollback=false,
       USid,
       _ShowMode=curly) || IKeypath <- IKPs,
                cs_trans:exists(Th, IKeypath) == true],
    stop_io(IoHandler).

get_j_delta(NewTh, OldTh, USid, IKPs) ->
    IoHandler = spawn_link(fun() -> iohandler() end),
    [cli_show:show_delta(
       IoHandler,
       USid,
       NewTh,
       OldTh,
       cs_server:get_ns_list(),
       _Visible=['*', full],
       _ExportedNS=no_export_none,
       _FiltersIKP=IKeypath,
       _NoAltName=true,
       _ShowNS=true,
       _NoDropElem=true,
       _NoExposeName=true,
       _IgnoreXdsFlags=0,
       _IsRollback=false,
       _Mode=cdb) || IKeypath <- IKPs,
                     cs_trans:exists(NewTh, IKeypath) == true],
    stop_io(IoHandler).

iohandler() ->
    iohandler([]).

iohandler(Acc) ->
    receive
        %% FIXME? Will we ever get config as unicode?
        {io_request, From, ReplyAs, {put_chars,_Encoding,M,F,A}} ->
            Res = apply(M,F,A),
            io_reply(From, ReplyAs, ok),
            iohandler([Res|Acc]);
        {progress_io, Chars} ->
            Res = Chars,
            iohandler([Res|Acc]);
        %% FIXME? Will we ever get config as unicode?
        {io_request, From, ReplyAs, {put_chars,_Encoding,Chars}} ->
            Res = Chars,
            io_reply(From, ReplyAs, ok),
            iohandler([Res|Acc]);
        {From, stop} ->
            From ! {self(), lists:flatten(lists:reverse(Acc))},
            exit(normal);
        Other ->
            error_logger:format("iohandler() got ~p~n", [Other]),
            %% FIXME? This doesn't really work since everything hangs
            iohandler(Acc)
    end.

stop_io(IoH) ->
    stop_io(IoH, long).

stop_io(IoH, GracePeriod) ->
    IoH ! {self(), stop},
    Timeout = if GracePeriod == long -> 5000; true -> 500 end,
    receive
        {IoH, Res} ->
            {ok, Res};
        {IoH, {error, Code, Bin}} when is_integer(Code), is_binary(Bin) ->
            {error, Code, Bin};
        {IoH, _Err} ->
            {error, ?CONFD_ERR_EXTERNAL, <<"socket write failure">>}
    after Timeout ->
            unlink(IoH), exit(IoH, stop),
            {error, ?CONFD_ERR_EXTERNAL, <<"blocking timeout">>}
    end.

io_reply(From, ReplyAs, Reply) ->
    From ! {io_reply, ReplyAs, Reply}.

%% in this example we store the config in a file system, but
%% it would just as easily be some central db
save_config(_DevName, {delta, []}) ->
    ok;
save_config(DevName, {full, Str}) ->
    FileName = "../db_store/"++?b2l(DevName)++".cfg",
    ok = file:write_file(FileName++".tmp", Str),
    ok = file:rename(FileName++".tmp", FileName);
save_config(DevName, {delta, Str}) ->
    %% We do this in two steps to avoid half-written config files.
    FileName = "../db_store/"++?b2l(DevName)++".cfg",
    file:write_file(FileName, Str, [append]).
