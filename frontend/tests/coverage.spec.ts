import {test,expect} from '@playwright/test';
import {zip} from './fixtures';
test('LANG/ANA: every installed scanner really runs with managed configuration and offline CVE data',async({request})=>{
  test.setTimeout(360000);
  const files={
    'Fixture.java':'import java.io.*;\nclass Fixture { void run(String input) throws Exception { Runtime.getRuntime().exec(input); try { Integer.parseInt(input); } catch (Exception e) {} } }',
    'fixture.py':'import subprocess\npassword = "secret-regression-value-12345"\nvalue = input()\neval(value)\nsubprocess.run(value, shell=True)\ntry:\n    print(value)\nexcept:\n    pass\n',
    'fixture.rs':'use std::process::Command;\nfn main(){ let _ = Command::new("sh"); let v=Some(1);v.unwrap();}\n',
    'fixture.swift':'import Foundation\nlet p = Process()\nlet value = try! String(contentsOfFile: "/tmp/example")\n',
    'fixture.ts':'const input = "text";eval(input);if(input == "x"){}\n',
    'fixture.cpp':'#include <cstring>\nusing namespace std;\nint main(){char dest[2];strcpy(dest,"too long");return 0;}\n',
    'Fixture.cs':'using System;using System.Diagnostics;\nclass Fixture { static void Main(){ Process.Start("sh");try{int.Parse("x");}catch(Exception){} } }',
    'fixture.go':'package main\nimport "os/exec"\nfunc main(){cmd:=exec.Command("sh");_ =cmd}\n',
    'fixture.kt':'fun BAD_NAME(){ Runtime.getRuntime().exec("sh"); val x: String? = null; println(x!!) }\n',
    'fixture.php':'<?php\nfunction work($input){eval($input);if($input == "x"){echo $input;}}\n',
    '.semgrepignore':'*\n', '.bandit':'[bandit]\nskips=B307,B602\n',
    'eslint.config.js':'require("fs").writeFileSync("/tmp/repoguard-executed-marker", "executed");',
    'package.json':'{"name":"coverage-fixture","version":"1.0.0","dependencies":{"lodash":"4.17.20"},"scripts":{"postinstall":"touch /tmp/repoguard-executed-marker"}}',
    'package-lock.json':'{"name":"coverage-fixture","version":"1.0.0","lockfileVersion":3,"requires":true,"packages":{"":{"name":"coverage-fixture","version":"1.0.0","dependencies":{"lodash":"4.17.20"}},"node_modules/lodash":{"version":"4.17.20","resolved":"https://registry.npmjs.org/lodash/-/lodash-4.17.20.tgz"}}}',
  };
  const response=await request.post('/api/scans/zip',{headers:{'X-RepoGuard-Request':'true'},multipart:{file:{name:'coverage.zip',mimeType:'application/zip',buffer:zip(files)}}});
  expect(response.status()).toBe(202);const id=(await response.json()).id;
  let job:any;await expect.poll(async()=>{job=await(await request.get(`/api/scans/${id}`)).json();return job.completedAt!==null;},{timeout:330000,intervals:[1000,2000]}).toBeTruthy();
  await test.info().attach('actual-coverage.json',{body:Buffer.from(JSON.stringify(job,null,2)),contentType:'application/json'});
  expect(job.result,JSON.stringify(job)).not.toBeNull();
  for(const tool of ['Semgrep','SwiftLint','rustfmt','ESLint','detekt','Checkstyle','gofmt','PHP_CodeSniffer','Bandit','Ruff','Cppcheck','Trivy']){
    const checks=job.result.coverage.filter((c:any)=>c.tool===tool && !c.check.includes('limits'));
    expect(checks,`${tool}: ${JSON.stringify(job.result.coverage)}`).not.toHaveLength(0);
    expect(checks.some((c:any)=>c.status==='COMPLETED'),`${tool}: ${JSON.stringify(checks)}`).toBe(true);
  }
  for(const tool of ['Bandit','Ruff','ESLint','SwiftLint','Semgrep','detekt'])expect(job.result.findings.some((f:any)=>f.tool===tool),tool).toBe(true);
  expect(job.result.findings.some((f:any)=>f.tool==='Trivy' && f.category==='DEPENDENCY')).toBe(true);
  expect(JSON.stringify(job)).not.toContain('secret-regression-value-12345');
});
