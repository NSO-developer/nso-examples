-module(ec_inventory_updater_sup).

-behaviour(supervisor).

%% API
-export([start_link/0]).

%% Supervisor callbacks
-export([init/1]).

-define(SERVER, ?MODULE).

%%%===================================================================
%%% API functions
%%%===================================================================

start_link() ->
    supervisor:start_link({local, ?SERVER}, ?MODULE, []).

%%%===================================================================
%%% Supervisor callbacks
%%%===================================================================
%%
%% CHILD SPECS (as I never can remember their format...)
%%
%%   {ChildId, StartFunc, Restart, Shutdown, Type, Modules}
%%
%%  ChildId   : Internal name used by the Supervisor, term().
%%  StartFunc : {M,F,A}
%%  Restart   : permanent | temporary | transient
%%
%%    permanent = process should always be restarted, no matter what
%%    temporary = process should never be restarted
%%    transient = restart process if it terminates non-normal
%%
%%  Shutdown  : time to specify the deadline on the termination
%%              (not respected by 'simple_one_for_one')
%%
%%  Type      : worker | supervisor
%%
%%  Modules   : [<callback-module-name>] | dynamic
%%

%% Helper macro for declaring children of supervisor
-define(CHILD(I, Type), {I,
                         {I, start_link, []},
                         permanent,
                         5000,
                         Type,
                         [I]}).


%%--------------------------------------------------------------------
init([]) ->
    RestartStrategy = one_for_one,
    MaxRestarts = 3,
    MaxSecondsBetweenRestarts = 10,

    SupFlags = {RestartStrategy, MaxRestarts, MaxSecondsBetweenRestarts},

    IU = ?CHILD(ec_inventory_updater_server, worker),

    {ok, {SupFlags, [IU]}}.
