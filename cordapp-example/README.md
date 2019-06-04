<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Example CorDapp

Welcome to the example CorDapp. This CorDapp is documented [here](http://docs.corda.net/tutorial-cordapp.html).

# Running the CorDapp

Starting all nodes and servers:

```
# All commands should be run from the root of the project
$ cd cordapp-example
# Gradle task to build four nodes with our CorDapp already installed on them
# ./gradlew deployNodes
# Should launch 4 new terminal windows
# Don't move focus from these windows until a node has started in each window
# Might take multiple tries for all 4 to properly start
$ workflows-kotlin/build/nodes/runnodes
# Each server needs to be run in its own window
$ ./gradlew runDealerServer
$ ./gradlew runPlayerAServer
$ ./gradlew runPlayerBServer
```

Creating a new Game:

```
# Initiate from the Dealer node
$ start com.example.flow.CreateGameFlow$Dealer players: [PlayerA, PlayerB]
# All nodes should be able to see the GameState
$ run vaultQuery contractStateType: com.example.state.GameState
```

Deal cards:

```
# Initiate from the Dealer node
$ start com.example.flow.DealFlow$Dealer cards: [{ suit: H, rank: "5" }, { suit: "D", rank: "5" }, { suit: "H", rank: "Q" }, { suit: "S", rank: "A" }], players: [PlayerA, PlayerB], gameId: 123605233
# Each of the two nodes should be able to see the 2 cards dealt to them
$ run vaultQuery contractStateType: com.example.state.CardState
```