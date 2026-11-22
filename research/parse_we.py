import re, sys, os, json
src = sys.argv[1]
out = []
BS = chr(92)
def strlit(s):
    return re.findall(r'"((?:[^"\\]|\\.)*)"', s)
def find_balanced(text, start):
    depth = 0; i = start; instr = False
    while i < len(text):
        c = text[i]
        if instr:
            if c == BS: i += 2; continue
            if c == '"': instr = False
        elif c == '"': instr = True
        elif c == "'" and i + 2 < len(text) and text[i + 2] == "'": i += 3; continue
        elif c == '(': depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0: return i
        i += 1
    return -1
def kv(body, key):
    m = re.search(r'\b' + key + r'\s*=\s*(\{[^}]*\}|"(?:[^"\\]|\\.)*"|\'.\'|[\w.]+)', body)
    if not m: return None
    v = m.group(1)
    if v.startswith("'"): return v[1]
    if v.startswith('{') or v.startswith('"'): return strlit(v)
    return v
for fn in sorted(os.listdir(src)):
    if not fn.endswith('.java'): continue
    t = open(os.path.join(src, fn), encoding='utf-8').read()
    for m in re.finditer(r'@(?:org\.enginehub\.piston\.annotation\.)?Command\(', t):
        e = find_balanced(t, m.end() - 1)
        body = t[m.end():e]
        name = kv(body, 'name'); name = name[0] if name else '?'
        aliases = kv(body, 'aliases') or []
        desc = ' '.join(kv(body, 'desc') or [])
        rest = t[e + 1:]
        pm = re.search(r'@CommandPermissions\(\{([^}]*)\}\)', rest[:600])
        perms = strlit(pm.group(1)) if pm else []
        mm = re.search(r'\n\s+(?:public |private |protected )?(?:static )?(?:final )?[\w<>\[\]., ?]+ (\w+)\(', rest)
        ps = mm.end() - 1; pe = find_balanced(rest, ps)
        params = rest[ps + 1:pe]
        args = []
        for a in re.finditer(r'@(Arg|Switch|ArgFlag)\(', params):
            ae = find_balanced(params, a.end() - 1)
            ab = params[a.end():ae]
            after = params[ae + 1:ae + 300]
            tm = re.match(r'\s*((?:@[\w.]+(?:\([^)]*\))?\s*)*)([\w.<>\[\]?, ]+?)\s+(\w+)\s*(,|$)', after)
            ptype = tm.group(2).strip() if tm else '?'
            pname = tm.group(3) if tm else '?'
            mods = re.findall(r'@(\w+)', tm.group(1)) if tm else []
            kind = a.group(1)
            args.append(dict(kind=kind,
                             flag=kv(ab, 'name') if kind != 'Arg' else None,
                             argname=(kv(ab, 'name') or [None])[0] if kind == 'Arg' else None,
                             desc=' '.join(kv(ab, 'desc') or []), default=kv(ab, 'def'),
                             type=ptype, param=pname, mods=mods))
        out.append(dict(file=fn[:-5], name=name, aliases=aliases, desc=desc, perms=perms,
                        method=mm.group(1), args=args))
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
# compact text listing
with open(sys.argv[3], 'w', encoding='utf-8') as f:
    cur = None
    for c in out:
        if c['file'] != cur:
            cur = c['file']; f.write('\n## ' + cur + '\n')
        sig = []
        for a in c['args']:
            if a['kind'] == 'Arg':
                d = a['default']
                nm = a['argname'] or a['param']
                s = nm + ':' + a['type'].split('.')[-1]
                if d is not None: s = '[' + s + '=' + ','.join(d) + ']'
                else: s = '<' + s + '>'
                sig.append(s)
            elif a['kind'] == 'Switch':
                sig.append('-' + str(a['flag']))
            else:
                sig.append('-' + str(a['flag']) + ' <' + a['type'].split('.')[-1] + '>')
        al = (' (' + ','.join(c['aliases']) + ')') if c['aliases'] else ''
        f.write(c['name'] + al + ' ' + ' '.join(sig) + '  -- ' + c['desc'] + '\n')
        for a in c['args']:
            if a['kind'] != 'Arg':
                f.write('    -' + str(a['flag']) + ': ' + a['desc'] + '\n')
print(len(out))
