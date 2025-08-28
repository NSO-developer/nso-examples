#!/usr/bin/env python3
import argparse
import os
import subprocess
from bs4 import BeautifulSoup
import re

# Remove all CLI extensions that do not prevent adding config allowed by a
# real device
p = "tailf_cli_prefix_"
filterList = [
    f'{p}autowizard', f'{p}boolean-no',
    f'{p}column-align', f'{p}x-stats',
    f'{p}column-width', f'{p}compact-stats',
    f'{p}completion-actionpoint', f'{p}configure-mode', f'{p}custom-error',
    f'{p}custom-range-actionpoint', f'{p}delayed-auto-commit',
    f'{p}delete-container-on-delete',
    f'{p}diff-after', f'{p}diff-before', f'{p}diff-create-after',
    f'{p}diff-create-before ', f'{p}diff-delete-after',
    f'{p}diff-delete-before', f'{p}diff-dependency', f'{p}diff-modify-after',
    f'{p}diff-modify-before', f'{p}diff-set-after', f'{p}diff-set-before',
    f'{p}disabled-info', f'{p}disallow-value', f'{p}display-empty-config',
    f'{p}display-joined', f'{p}display-separated',
    f'{p}embed-no-on-delete', f'{p}enforce-table',
    f'{p}exit-command', f'{p}explicit-exit', f'{p}expose-key-name',
    f'{p}expose-ns-prefix', f'{p}full-command', f'{p}full-no',
    f'{p}full-show-path', f'{p}ignore-modified',
    f'{p}incomplete-command', f'{p}incomplete-no', f'{p}incomplete-show-path',
    f'{p}instance-info-leafs', f'{p}key-format', f'{p}list-syntax',
    f'{p}max-words', f'{p}min-column-width', f'{p}mode-name',
    f'{p}mode-name-actionpoint', f'{p}mount-point',
    f'{p}multi-line-prompt', f'{p}no-key-completion',
    f'{p}no-match-completion', f'{p}no-name-on-delete',
    f'{p}no-value-on-delete', f'{p}only-in-autowizard', f'{p}oper-info',
    f'{p}operational-mode', f'{p}preformatted',
    f'{p}recursive-delete', f'{p}remove-before-change', f'{p}replace-all',
    f'{p}reset-container', f'{p}run-template', f'{p}run-template-enter',
    f'{p}run-template-footer', f'{p}run-template-legend',
    f'{p}short-no', f'{p}show-config',
    f'{p}show-long-obu-diffs', f'{p}show-no', f'{p}show-obu-comments',
    f'{p}show-order-tag', f'{p}show-order-taglist', f'{p}show-template',
    f'{p}show-template-enter', f'{p}show-template-footer',
    f'{p}show-template-legend', f'{p}show-with-default', f'{p}strict-leafref',
    f'{p}suppress-error-message-value', f'{p}suppress-key-sort',
    f'{p}suppress-leafref-in-diff', f'{p}suppress-no', f'{p}suppress-quotes',
    f'{p}suppress-show-conf-path', f'suppress-shortenabled{p}',
    f'{p}suppress-show-match', f'{p}suppress-show-path',
    f'{p}suppress-silent-no', f'{p}suppress-table',
    f'{p}suppress-validation-warning-prompt', f'{p}suppress-warning',
    f'{p}suppress-wildcard', f'{p}table-footer', f'{p}table-legend',
    f'{p}trim-default', f'{p}value-display-template', f'{p}template-string'
]


def tailf_ned_sanitize(yang_file):
    my_env = os.environ.copy()
    ncs_dir = os.environ['NCS_DIR']
    yang_path = yang_file.rsplit('/', 1)[0]
    result = subprocess.run(
        ['pyang', '--path', yang_path, '--path', ncs_dir,
         '--format', 'yin', yang_file], stdout=subprocess.PIPE, env=my_env,
        encoding='utf-8')
    yin_content = result.stdout
    yin_content = yin_content.replace('tailf:cli-', 'tailf_cli_prefix_')
    yin_content = yin_content.replace('tailf:alt-name',
                                      'tailf_alt_name')
    yin_content = yin_content.replace('tailf:code-name',
                                      'tailf_code_name')
    yin_content = yin_content.replace('tailf:', 'tailf_prefix_')
    yin_content = yin_content.replace('cli:', 'tailf_prefix_')
    yin_content = yin_content.replace('name=', 'yname=')
    yin_soup = BeautifulSoup(yin_content, "xml")
    module = yin_soup.find('module')
    if module is not None:
        if "tailf" in module['yname']:
            module_name = module['yname'].replace("tailf", "netsim")
        elif "junos" in module['yname']:
            module_name = module['yname'] = "netsim-ned-juniper-junos"
        module['yname'] = module_name
    else:
        exit(1)
    cli_import = yin_soup.find('import', module=re.compile('cliparser'))
    if cli_import is not None:
        cli_import.decompose()
    for revision in yin_soup.find_all('revision'):
        revision.clear()
    for tailf_cli_extension in yin_soup.find_all(re.compile(
                                                'tailf_cli_prefix_')):
        for extension in filterList:
            if (tailf_cli_extension.name is not None and extension in
                    tailf_cli_extension.name):
                tailf_cli_extension.decompose()
                break
    for tailf_extension in yin_soup.find_all(re.compile('tailf_prefix_')):
        tailf_extension.decompose()
    for extension in yin_soup.find_all('extension'):
        extension.decompose()
    tailfned = yin_soup.find('container', yname='tailfned')
    if tailfned is not None:
        tailfned.decompose()
    for when in yin_soup.find_all('when'):
        when.decompose()
    typedef_nedcom = yin_soup.find('typedef', yname=re.compile('NEDCOM'))
    if typedef_nedcom is not None:
        typedef_nedcom.decompose()
    for type_nedcom in yin_soup.find_all('type', yname=re.compile('NEDCOM')):
        type_nedcom['yname'] = "string"
    if "junos" in yang_file:
        for type_enum in yin_soup.find_all('type', yname='enumeration'):
            type_enum.clear()
            type_enum['yname'] = "string"
        for union in yin_soup.find_all('type', yname='union'):
            union.clear()
            union['yname'] = "string"
    for type_leafref in yin_soup.find_all('type', yname='leafref'):
        type_leafref.clear()
        type_leafref['yname'] = "string"
    for pattern in yin_soup.find_all('pattern'):
        pattern.decompose()
    for yrange in yin_soup.find_all('range'):
        yrange.decompose()
    for length in yin_soup.find_all('length'):
        length.decompose()
    for max_elements in yin_soup.find_all('max-elements'):
        max_elements.decompose()
    for min_elements in yin_soup.find_all('min-elements'):
        min_elements.decompose()
    for default in yin_soup.find_all('default'):
        default.decompose()
    for description in yin_soup.find_all('description'):
        description.decompose()
    for reference in yin_soup.find_all('reference'):
        reference.decompose()

    with open(f'{module_name}.yin', "w") as yin_file:
        yin_soup_str = str(yin_soup)
        yin_soup_str = yin_soup_str.replace('tailf_cli_prefix_', 'tailf:cli-')
        yin_soup_str = yin_soup_str.replace('tailf_alt_name',
                                            'tailf:alt-name')
        yin_soup_str = yin_soup_str.replace('tailf_code_name',
                                            'tailf:code-name')
        yin_soup_str = yin_soup_str.replace('tailf_prefix_', 'tailf:')
        yin_soup_str = yin_soup_str.replace('yname=', 'name=')
        yin_file.write(yin_soup_str)

    yang_content = subprocess.run(
        ['pyang', '--format', 'yang', '--path', yang_path,
         '--path', ncs_dir, f'{module_name}.yin'],
        stdout=subprocess.PIPE, env=my_env,
        encoding='utf-8')
    print(yang_content.stdout, end='')


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument('filename', nargs=1, type=str,
                        help='<file> YANG module to be sanitized')
    args = parser.parse_args()
    tailf_ned_sanitize(args.filename[0])
