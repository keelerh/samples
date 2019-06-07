"use strict";

const express = require('express')
const Proxy = require('braid-client').Proxy;

const app = express()

// Connects to Braid running on the node.
let braid = new Proxy({
  url: "http://localhost:20009/api/"
}, onOpen, onClose, onError, { strictSSL: false });

 const util = require("util")


function onOpen() { console.log('Connected to node.'); }
function onClose() { console.log('Disconnected from node.'); }
function onError(err) { console.error(err); process.exit(); }

app.get('/test', (req, res) => {

  console.log(util.inspect(braid))
  console.log(util.inspect(req.body))
  const partyName = ""
  const cards = [{suit:"SPKIKE", rank: "ACE"}];

  braid.flows.TestFlow(cards, partyName)

    .then(result => res.send("Hey, you're speaking to " + result + "!"))
    .catch(err => res.status(500).send(err));    
});

app.listen(3000, () => console.log('Server listening on port 3000!'))