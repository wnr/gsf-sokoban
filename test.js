/* jshint node: true */

'use strict';

var chalk = require('chalk');
var exec = require('child_process').exec;

console.log('');
printHeader('Google Search First Sokoban Test');
console.log('');

removeDir('temp', function(err) {
  if(err) throw err;

  createDir('temp', function(err) {
    if(err) throw err;

    createDir('temp/out.sokoban', function(err) {
      if(err) throw err;

      compile(function(err) {
        if(err) throw err;
      });
    });
  });
});



function printHeader(str, color) {
  var length = 50;

  if(!color) {
    color = chalk.yellow;
  }

  var leftSpace = new Array(length/2 - 1 - Math.floor(str.length/2)).join(' ');
  var rightSpace = new Array(length/2 - leftSpace - Math.ceil(str.length/2)).join(' ');

  console.log(color(new Array(length).join('#')));
  console.log(color('#') + leftSpace + str + rightSpace + color('#'));
  console.log(color(new Array(50).join('#')));
}

function printJob(str) {
  var length = 44;

  str += '...';

  process.stdout.write(str + new Array(length - str.length).join(' '));
}

function printJobDone() {
  process.stdout.write('[' + chalk.green('done') + ']\n');
}

function printJobFailed() {
  process.stdout.write('[' + chalk.red('failed') + ']\n');
}

function removeDir(dir, cb) {
  execute('Removing directory ' + dir, 'rm -rf ' + dir, cb);
}

function createDir(dir, cb) {
  execute('Creating directory ' + dir, 'mkdir ' + dir, cb);
}

function compile(cb) {
  execute('Compiling', 'javac src/*.java -d temp/out.sokoban -encoding UTF-8', cb);
}

function execute(job, cmd, cb) {
  if(!cmd) {
    cmd = job;
  }

  if(typeof cmd === 'function') {
    cb = cmd;
    cmd = job;
  }

  printJob(job);
  exec(cmd, function(err, stdout, stderr) {
    if(err || stderr) {
      printJobFailed();

      if(cb) {
        return cb(err || new Error(stderr));
      } else {
        throw err || new Error(stderr);
      }
    }

    printJobDone();

    if(cb) {
      cb(stdout);
    }
  });
}