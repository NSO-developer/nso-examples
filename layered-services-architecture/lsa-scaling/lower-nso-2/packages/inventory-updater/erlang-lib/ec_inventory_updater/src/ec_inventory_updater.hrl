%%%-------------------------------------------------------------------
%%% @doc Inventory Updater
%%%      Internal definitions and records
%%%-------------------------------------------------------------------


-define(i2l(X), integer_to_list(X)).
-define(l2i(X), list_to_integer(X)).
-define(l2b(X), list_to_binary(X)).
-define(l2f(X), list_to_float(X)).
-define(b2l(X), binary_to_list(X)).
-define(a2l(X), atom_to_list(X)).
-define(l2a(X), list_to_atom(X)).
-define(t2l(X), tuple_to_list(X)).
-define(l2t(X), list_to_tuple(X)).

-define(stack(), try throw(1) catch _:_ -> erlang:get_stacktrace() end).
-define(bit_is_set(Fs, F),       ((Fs) band (F) =/= 0)).  % any bit in F is set

-define(iof(F,A), io:format(F,A)).
