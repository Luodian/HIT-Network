#include "sysInclude.h"
#include <map>
using std::map;
// system support
extern void fwd_LocalRcv(char *pBuffer, int length);

extern void fwd_SendtoLower(char *pBuffer, int length, unsigned int nexthop);

extern void fwd_DiscardPkt(char *pBuffer, int type);

extern unsigned int getIpv4Address( );

// implemented by students
struct route_table
{
    int dest;
    int nexthop;
};

map<int,int> mytable;//定义了我自己的路由表，空间不限

void stud_Route_Init()
{
    mytable.clear();//清空路由表
    return;
}

void stud_route_add(stud_route_msg *proute)// 添加一个新的表项
{
    route_table t;
    t.dest = (ntohl(proute->dest)) & (0xffffffff << (32 - htonl(proute->masklen)));
    t.nexthop = ntohl(proute->nexthop);
    map[t.dest] = t.nexthop;
    return;
}


int stud_fwd_deal(char *pBuffer, int length)
{
    int IHL = pBuffer[0] & 0xf;
    int TTL = (int)pBuffer[8];
    int Head_Checksum = ntohs(*(unsigned short*)(pBuffer + 10));
    int Dst_IP = ntohl(*(unsigned*)(pBuffer + 16));

    if (Dst_IP == getIpv4Address())
    {
        fwd_LocalRcv(pBuffer, length);
        return 0;
    }

    if (TTL <= 0)
    {
        fwd_DiscardPkt(pBuffer, STUD_FORWARD_TEST_TTLERROR);
        return 1;
    }
    // if can't find target k-v.
    if (mytable.find(Dst_IP) != mytable.end())
    {
        char *buffer = new char[length];
        memcpy(buffer, pBuffer, length);
        // ttl -= 1
        buffer[8]--;
        int sum = 0, i = 0;
        unsigned short Local_Checksum = 0;
        // caculate checksum
        for (; i < 2 * IHL; i++)
        {
            if (i != 5)
            {
                sum += (buffer[2 * i] << 8) + (buffer[2 * i + 1]);
                sum %= 65535;
            }
        }
        // 计算反码
        Local_Checksum = htons(0xffff - (unsigned short)sum);
        memcpy(buffer + 10, &Local_Checksum, 2);
        // 将查找到的下一条地址填入目的IP
        fwd_SendtoLower(buffer, length, mytable[Dst_IP]);
        return 0;
    }
    fwd_DiscardPkt(pBuffer, STUD_FORWARD_TEST_NOROUTE);
    return 1;
}
