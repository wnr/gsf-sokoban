/* jshint node: true */

'use strict';

//-----------------------------------------------------------------------------
// Dependencies
//-----------------------------------------------------------------------------

var chalk = require('chalk');
var exec = require('child_process').exec;
var fs = require('fs');
var os = require('os');
var ProgessBar = require('progress');

//-----------------------------------------------------------------------------
// Main execution point
//-----------------------------------------------------------------------------

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

        readTestData('test.data', function(err, tests) {
          function checkDone() {
            if(numExecuted >= numTests) {
              console.log('');

              var elapsed = new Date() - bar.start;

              printResult(numExecuted, numPassed, numFailed, elapsed);

                // removeDir('temp', function(err) {
                //   //Done.
                // });
              
              return true;
            }

            return false;
          }

          function runTest(data) {
            numRunning++;
            test(data, function(err, result) {
              numRunning--;

              if(err) {
                numFailed++;
                numExecuted++;
                bar.tick();
                throw err;
              }

              if(result) {
                numPassed++;
              } else {
                numFailed++;
              }

              ++numExecuted;

              bar.tick();

              if(!checkDone()) {
                if(numRunning < cpus && numExecuted + numRunning < numTests) {
                  runTest(tests.shift());
                }
              }
            });
          }

          var numTests = tests.length;
          var numPassed = 0;
          var numFailed = 0;
          var numExecuted = 0;

          var numRunning = 0;

          var cpus = os.cpus().length;

          var bar = new ProgessBar('[:bar] :current/:total (:percent) :elapsed s', {
            width: numTests <= 100 ? numTests : 100,
            total: numTests,
            complete: '●',
            incomplete: '◦'
          });

          console.log('\n' + chalk.yellow(numTests + ' tests to be executed by ' + cpus + ' cores.') + '\n');

          for(var i = 0; i < cpus; i++) {
            runTest(tests.shift());
          }
        });
      });
    });
  });
});

//-----------------------------------------------------------------------------
// Helper functions
//-----------------------------------------------------------------------------

function printResult(total, passed, failed, elapsed) {
  // printHeader('Test results');
  // console.log('');

  console.log('Total:    ' + total);
  console.log('Passed:   ' + chalk.green(passed.toString()));
  console.log('Failed:   ' + chalk.red(failed.toString()));
  console.log('Time:     ' + chalk.yellow((elapsed / 1000).toFixed(1) + ' s'));
}

function test(map, cb) {
  execute('echo "' + map + '" | java -cp temp/out.sokoban Main > /dev/null', cb);
}

function readTestData(file, cb) {
  if(!cb) {
    throw new Error('Callback required.');
  }

  printJob('Reading test data ' + file);
  fs.readFile(file, 'utf8', function(err, data) {
    if(err) {
      printJobFailed();
      return cb(err);
    }

    var result = data.split(/;LEVEL \d+/).splice(1);

    printJobDone();

    cb(null, result);
  });
}

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
    job = null;
  }

  if(typeof cmd === 'function') {
    cb = cmd;
    cmd = job;
    job = null;
  }

  if(job) {
    printJob(job);
  }

  exec(cmd, function(err, stdout, stderr) {
    if(err || stderr) {
      if(job) {
        printJobFailed();
      }

      if(cb) {
        return cb(err || new Error(stderr));
      } else {
        throw err || new Error(stderr);
      }
    }

    if(job) {
      printJobDone();
    }

    if(cb) {
      cb(null, stdout);
    }
  });
}