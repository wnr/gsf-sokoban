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

var config = {
  tests: 20
};

config.tests = process.argv[2] || config.tests;

printHeader('Google Search First Sokoban Test');

removeDir('temp', function(err) {
  if(err) { throw err; }

  createDir('temp', function(err) {
    if(err) { throw err; }

    createDir('temp/out.sokoban', function(err) {
      if(err) { throw err; }

      compile(function(err) {
        if(err) { throw err; }

        readTestData('test.data', config.tests, function(err, tests) {
          var tester = new Tester(tests);
          tester.run(function done() {
            tester.printResults();
            tester.printExceptions();
          });
        });
      });
    });
  });
});

//-----------------------------------------------------------------------------
// Classes
//-----------------------------------------------------------------------------

function Tester(maps) {
  if(!Array.isArray(maps)) {
    throw new Error('Maps data required');
  }

  this.maps = maps;
  this.tests = maps.length;
  this.executed = 0;
  this.passed = 0;
  this.failed = 0;
  this.running = 0;
  this.exceptions = [];
  this.elapsed = 0;
  this.cpus = os.cpus().length;

  this.bar = new ProgessBar('[:bar] :current/:total (:percent) :elapsed s', {
    width: (this.tests <= 100 ? this.tests : 100) + 1,
    total: this.tests,
    complete: '●',
    incomplete: '◦'
  });
}

Tester.prototype.run = function(print, cb) {
  if(!cb && typeof print === 'function') {
    cb = print;
    print = null;
  }

  if(!print) {
    print = true;
  }

  if(print) {
    console.log('\n' + chalk.yellow(this.tests + ' tests to be executed by ' + this.cpus + ' cores.') + '\n');
  }

  for(var i = 0; i < this.cpus && i < this.tests; i++) {
    var m = this.maps.shift();
    this.test(m.map, m.level, cb);
  }
};

Tester.prototype.isDone = function() {
  if(this.executed >= this.tests) {
    this.elapsed = new Date() - this.bar.start;
    return true;
  }

  return false;
};

Tester.prototype.test = function(map, level, cb) {
  this.running++;

  var self = this;

  test(map, function(err, result) {
    self.running--;
    self.executed++;

    if(err || !result) {
      self.failed++;

      if(err) {
        self.exceptions.push({
          test: 'Test ' + level,
          err: err.message,
          cmd: 'echo "' + map + '" | java -cp temp/out.sokoban Main'
        });
      }
    }

    if(result) {
      self.passed++;
    }

    self.bar.tick();

    if(self.isDone()) {
      if(cb) {
        cb();
      }
    } else {
      if(self.running < self.cpus && self.executed + self.running < self.tests) {
        var test = self.maps.shift();
        self.test(test.map,  test.level, cb);
      }
    }
  });
};

Tester.prototype.printExceptions = function() {
  console.log('');

  if(this.exceptions.length) {
    printHeader('Exceptions', chalk.red);

    for(var i = 0; i < this.exceptions.length; i++) {
      console.log(chalk.red(this.exceptions[i].test) + '\n' + this.exceptions[i].err + '\n' /* + chalk.yellow('Test with: ' + exceptions[i].cmd) */);
    }
  }

  console.log('\n');
};

Tester.prototype.printResults = function() {
  console.log('Total:    ' + this.total);
  console.log('Passed:   ' + chalk.green(this.passed.toString()));
  console.log('Failed:   ' + chalk.red(this.failed.toString()));
  console.log('Time:     ' + chalk.yellow((this.elapsed / 1000).toFixed(1) + ' s'));
};

//-----------------------------------------------------------------------------
// Helper functions
//-----------------------------------------------------------------------------

function test(map, cb) {
  execute('echo "' + map + '" | java -cp temp/out.sokoban Main > /dev/null', cb);
}

function readTestData(file, limit, cb) {
  if(!cb) {
    throw new Error('Callback required.');
  }

  printJob('Reading test data ' + file);
  fs.readFile(file, 'utf8', function(err, data) {
    if(err) {
      printJobFailed();
      cb(err);
      return;
    }

    var result = data.split(/;LEVEL \d+/).splice(1, limit);

    for(var i = 0; i < result.length; i++) {
      result[i] = {
        level: i+1,
        map: result[i]
      };
    }

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

  console.log('\n' + color(new Array(length).join('#')));
  console.log(color('#') + leftSpace + str + rightSpace + color('#'));
  console.log(color(new Array(50).join('#')) + '\n');
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
        cb(err || new Error(stderr));
        return;
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