-module(ec_vlan).

-behaviour(application).

%% API
%% -export([ping/0]).

%% Application callbacks
-export([start/2, stop/1]).

%%%===================================================================
%%% API
%%%===================================================================

%% ping() ->
%%     ec_vlan_server:ping().

%%%===================================================================
%%% Application callbacks
%%%===================================================================

start(_StartType, _StartArgs) ->
    case ec_vlan_sup:start_link() of
        {ok, Pid} ->
            {ok, Pid};
        Error ->
            Error
    end.

stop(_State) ->
    ok.

%%%===================================================================
%%% Internal functions
%%%===================================================================
