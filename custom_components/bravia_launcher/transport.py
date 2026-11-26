"""Transport policy for each paired screen."""
from ipaddress import IPv4Address, IPv4Network

PRIVATE = tuple(map(IPv4Network, ("10.0.0.0/8","172.16.0.0/12","192.168.0.0/16")))

def private_ip(value):
    try:
        ip = IPv4Address(value)
    except ValueError:
        return False
    return any(ip in net for net in PRIVATE)

def allowed(request, options):
    if not options.get("lan_http"):
        return request.secure
    peer = request.transport.get_extra_info("peername") if request.transport else None
    return bool(options.get("lan_http") and peer and private_ip(peer[0])
                and peer[0] == options.get("tv_ip")
                and not any(h in request.headers for h in ("Forwarded","X-Forwarded-For","X-Forwarded-Proto")))
