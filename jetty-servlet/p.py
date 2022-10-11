import os
import json

cur = os.getcwd()

output = []

with open("tests.txt",mode="r",encoding="utf-8") as f:
    for line in f:
        testClass = line.strip()
        path = cur + '/src/test/java/' + testClass.replace('.', '/') + '.java'

        with open(path, mode="r",encoding="utf-8") as sf:
            next = False
            for l in sf:
                if "@Test" in l:
                    next = True
                    continue

                if next:
                    next = False
                    testName = l.strip().split(' ')[2][:-2]
                    print(line.strip() + '#' + testName)
                    output.append(line.strip() + '#' + testName)


with open("test_method_list.json",mode="w",encoding="utf-8") as f:
    f.write(json.dumps(output))