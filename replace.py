import os
import sys

def replace_in_files(directory):
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith('.java') or file.endswith('.xml'):
                path = os.path.join(root, file)
                try:
                    with open(path, 'r', encoding='utf-8') as f:
                        content = f.read()
                except UnicodeDecodeError:
                    continue

                new_content = content.replace('net.osmand.plus.carlauncher', 'app.organicmaps.carlauncher')
                new_content = new_content.replace('net.osmand.plus', 'app.organicmaps')
                new_content = new_content.replace('OsmandApplication', 'MwmApplication')

                if content != new_content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(new_content)

if __name__ == '__main__':
    replace_in_files(sys.argv[1])
